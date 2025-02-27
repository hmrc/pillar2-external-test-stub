/*
 * Copyright 2024 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.pillar2externalteststub.controllers

import play.api.Logging
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.helpers.SubscriptionHelper.retrieveSubscription
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.{DuplicateSubmissionError, MissingPLRReference, SubscriptionNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSimpleError.{InvalidJsonError, SAPError}
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitUKTRController @Inject() (
  cc:                  ControllerComponents,
  authFilter:          AuthActionFilter,
  repository:          UKTRSubmissionRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  private def validatePillar2Id(pillar2Id: Option[String]): Either[Result, String] =
    pillar2Id
      .filter(pillar2Regex.matches)
      .toRight(UnprocessableEntity(Json.toJson(DetailedErrorResponse(MissingPLRReference))))

  def submitUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")) match {
      case Left(error) => Future.successful(error)
      case Right(plrReference) =>
        plrReference match {
          case ServerErrorPlrId => Future.successful(InternalServerError(Json.toJson(SAPError)))
          case _ =>
            retrieveSubscription(plrReference)._2 match {
              case _: SubscriptionSuccessResponse => validateRequest(plrReference, request)
              case _ => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(SubscriptionNotFound(plrReference)))))
            }
        }
    }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    implicit val os = organisationService

    def validateAccountingPeriod(submission: UKTRSubmission): Future[Either[Result, (UKTRSubmission, TestOrganisationWithId)]] =
      organisationService
        .getOrganisation(plrReference)
        .map { org =>
          if (
            org.organisation.accountingPeriod.startDate.isEqual(submission.accountingPeriodFrom) &&
            org.organisation.accountingPeriod.endDate.isEqual(submission.accountingPeriodTo)
          ) {
            Right((submission, org))
          } else {
            Left(
              UnprocessableEntity(
                Json.toJson(
                  DetailedErrorResponse(
                    UKTRDetailedError.InvalidAccountingPeriod(
                      submission.accountingPeriodFrom.toString,
                      submission.accountingPeriodTo.toString,
                      org.organisation.accountingPeriod.startDate.toString,
                      org.organisation.accountingPeriod.endDate.toString
                    )
                  )
                )
              )
            )
          }
        }
        .recover { case _ =>
          Left(UnprocessableEntity(Json.toJson(DetailedErrorResponse(UKTRDetailedError.RequestCouldNotBeProcessed))))
        }

    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        validateAccountingPeriod(uktrRequest).flatMap {
          case Left(error) => Future.successful(error)
          case Right((submission: UKTRLiabilityReturn, org)) =>
            UKTRLiabilityReturn.uktrSubmissionValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  repository.findDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo).flatMap {
                    case true => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(DuplicateSubmissionError))))
                    case false =>
                      if (org.organisation.orgDetails.domesticOnly) {
                        repository.insert(submission, plrReference).map(_ => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
                      } else {
                        repository
                          .insert(submission, plrReference)
                          .map(_ => Created(Json.toJson(LiabilityReturnSuccess.successfulNewLiabilityResponse)))
                      }
                  }
              }
            }
          case Right(_) => Future.successful(BadRequest(Json.toJson(InvalidJsonError("Invalid submission type"))))
        }
      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        validateAccountingPeriod(nilReturnRequest).flatMap {
          case Left(error) => Future.successful(error)
          case Right((submission: UKTRNilReturn, _)) =>
            UKTRNilReturn.uktrNilReturnValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  repository
                    .findDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo)
                    .flatMap {
                      case true  => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(DuplicateSubmissionError))))
                      case false => repository.insert(submission, plrReference).map(_ => Created(Json.toJson(successfulNilReturnResponse)))
                    }
              }
            }
          case Right(_) => Future.successful(BadRequest(Json.toJson(InvalidJsonError("Invalid submission type"))))
        }
      case JsError(errors) =>
        val errorMessage = errors
          .map { case (path, validationErrors) =>
            val fieldName     = path.toJsonString
            val errorMessages = validationErrors.map(_.message).mkString(", ")
            s"Field: $fieldName: $errorMessages"
          }
          .mkString("; ")
        Future.successful(BadRequest(Json.toJson(InvalidJsonError(errorMessage))))
      case _ => Future.successful(BadRequest(Json.toJson(InvalidJsonError())))
    }
  }
}
