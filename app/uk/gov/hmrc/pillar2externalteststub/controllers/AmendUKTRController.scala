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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.{MissingPLRReference, SubscriptionNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendUKTRController @Inject() (
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

  def amendUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")) match {
      case Left(error) => Future.successful(error)
      case Right(plrReference) =>
        plrReference match {
          case ServerErrorPlrId => Future.successful(InternalServerError(Json.toJson(UKTRSimpleError.SAPError)))
          case _ =>
            retrieveSubscription(plrReference)._2 match {
              case _: SubscriptionSuccessResponse => validateRequest(plrReference, request)
              case _ => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(SubscriptionNotFound(plrReference)))))
            }
        }
    }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    def validateSubmissionExists(submission: UKTRSubmission): Future[Either[Result, UKTRSubmission]] =
      repository.findByPillar2Id(plrReference).flatMap {
        case None    => Future.successful(Left(UnprocessableEntity(Json.toJson(DetailedErrorResponse(UKTRDetailedError.RequestCouldNotBeProcessed)))))
        case Some(_) => Future.successful(Right(submission))
      }

    def validateAccountingPeriod(submission: UKTRSubmission): Future[Either[Result, UKTRSubmission]] =
      organisationService
        .getOrganisation(plrReference)
        .map { org =>
          if (!submission.isValidAccountingPeriod) {
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
          } else if (
            org.organisation.accountingPeriod.startDate.isEqual(submission.accountingPeriodFrom) &&
            org.organisation.accountingPeriod.endDate.isEqual(submission.accountingPeriodTo)
          ) {
            Right(submission)
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
          case Right(_) =>
            validateSubmissionExists(uktrRequest).flatMap {
              case Left(error) => Future.successful(error)
              case Right(_) =>
                UKTRLiabilityReturn.uktrSubmissionValidator(plrReference)(organisationService, ec).flatMap { validator =>
                  Future.successful(validator.validate(uktrRequest).toEither).flatMap {
                    case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                    case Right(_) =>
                      repository.update(uktrRequest, plrReference).map {
                        case Left(error)  => UnprocessableEntity(Json.toJson(error))
                        case Right(true)  => Ok(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                        case Right(false) => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                      }
                  }
                }
            }
        }
      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        validateAccountingPeriod(nilReturnRequest).flatMap {
          case Left(error) => Future.successful(error)
          case Right(_) =>
            validateSubmissionExists(nilReturnRequest).flatMap {
              case Left(error) => Future.successful(error)
              case Right(_) =>
                UKTRNilReturn.uktrNilReturnValidator(plrReference)(organisationService, ec).flatMap { validator =>
                  Future.successful(validator.validate(nilReturnRequest).toEither).flatMap {
                    case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                    case Right(_) =>
                      repository.update(nilReturnRequest, plrReference).map {
                        case Left(error)  => UnprocessableEntity(Json.toJson(error))
                        case Right(true)  => Ok(Json.toJson(NilReturnSuccess.successfulNilReturnResponse))
                        case Right(false) => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                      }
                  }
                }
            }
        }
      case JsError(errors) =>
        val errorMessage = errors
          .map { case (path, validationErrors) =>
            val fieldName     = path.toJsonString
            val errorMessages = validationErrors.map(_.message).mkString(", ")
            s"Field: $fieldName: $errorMessages"
          }
          .mkString("; ")
        Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError(errorMessage))))
      case _ => Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError())))
    }
  }
}
