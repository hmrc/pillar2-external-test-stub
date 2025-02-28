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
import uk.gov.hmrc.pillar2externalteststub.models.error._
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.SubscriptionNotFound
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.{DuplicateSubmissionError, MissingPLRReference}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes.INTERNAL_SERVER_ERROR_500
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
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

  private def validatePillar2Id(pillar2Id: Option[String]): Future[String] =
    pillar2Id match {
      case Some(id) if pillar2Regex.matches(id) => Future.successful(id)
      case other                                => Future.failed(InvalidPillar2Id(other))
    }

  def submitUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { plr =>
        if (plr == ServerErrorPlrId) {
          Future.successful(
            InternalServerError(
              Json.obj(
                "error" -> Json.obj(
                  "code"    -> "500",
                  "message" -> "Internal server error",
                  "logID"   -> "C0000000000000000000000000000500"
                )
              )
            )
          )
        } else {
          validateRequest(plr, request)
        }
      }
      .recover { case _: InvalidPillar2Id =>
        UnprocessableEntity(Json.toJson(DetailedErrorResponse(MissingPLRReference)))
      }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    implicit val os = organisationService

    retrieveSubscription(plrReference)._2 match {
      case _: SubscriptionSuccessResponse => processValidRequest(plrReference, request)
      case _ => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(SubscriptionNotFound(plrReference)))))
    }
  }

  private def processValidRequest(plrReference: String, request: Request[JsValue])(implicit
    ec:                                         ExecutionContext,
    os:                                         OrganisationService
  ): Future[Result] = {
    def validateAccountingPeriod[T <: UKTRSubmission](submission: T): Future[T] =
      os.getOrganisation(plrReference)
        .flatMap { org =>
          if (
            org.organisation.accountingPeriod.startDate.isEqual(submission.accountingPeriodFrom) &&
            org.organisation.accountingPeriod.endDate.isEqual(submission.accountingPeriodTo)
          ) {
            Future.successful(submission)
          } else {
            Future.failed(
              InvalidAccountingPeriod(
                submission.accountingPeriodFrom.toString,
                submission.accountingPeriodTo.toString,
                org.organisation.accountingPeriod.startDate.toString,
                org.organisation.accountingPeriod.endDate.toString
              )
            )
          }
        }
        .recoverWith {
          case _: DatabaseError           => Future.failed(DatabaseError("Failed to validate accounting period"))
          case _: OrganisationNotFound    => Future.failed(OrganisationNotFound(plrReference))
          case e: InvalidAccountingPeriod => Future.failed(e)
          case _: RuntimeException        => Future.failed(new RuntimeException("Request could not be processed"))
          case _ => Future.failed(DatabaseError("Request could not be processed"))
        }

    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        validateAccountingPeriod(uktrRequest)
          .flatMap { submission =>
            UKTRLiabilityReturn.uktrSubmissionValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  repository.isDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo).flatMap {
                    case true => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(DuplicateSubmissionError))))
                    case false =>
                      repository.insert(submission, plrReference).map(_ => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
                  }
              }
            }
          }
          .recoverWith {
            case e: InvalidAccountingPeriod =>
              Future.successful(
                UnprocessableEntity(
                  Json.obj(
                    "errors" -> Json.obj(
                      "code" -> "003",
                      "text" -> s"Accounting period (${e.submittedStart} to ${e.submittedEnd}) does not match the registered period (${e.registeredStart} to ${e.registeredEnd})"
                    )
                  )
                )
              )
            case _: OrganisationNotFound =>
              Future.successful(
                UnprocessableEntity(
                  Json.obj(
                    "errors" -> Json.obj(
                      "code" -> "001",
                      "text" -> s"Organisation not found for PLR reference: $plrReference"
                    )
                  )
                )
              )
            case _: RuntimeException =>
              Future.successful(
                UnprocessableEntity(
                  Json.obj(
                    "errors" -> Json.obj(
                      "code" -> "003",
                      "text" -> "Request could not be processed"
                    )
                  )
                )
              )
            case _ =>
              Future.successful(
                InternalServerError(
                  Json.obj(
                    "error" -> Json.obj(
                      "code"    -> INTERNAL_SERVER_ERROR_500,
                      "message" -> "Internal server error",
                      "logID"   -> "C0000000000000000000000000000500"
                    )
                  )
                )
              )
          }

      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        validateAccountingPeriod(nilReturnRequest)
          .flatMap { submission =>
            UKTRNilReturn.uktrNilReturnValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  repository.isDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo).flatMap {
                    case true  => Future.successful(UnprocessableEntity(Json.toJson(DetailedErrorResponse(DuplicateSubmissionError))))
                    case false => repository.insert(submission, plrReference).map(_ => Created(Json.toJson(successfulNilReturnResponse)))
                  }
              }
            }
          }
          .recoverWith {
            case e: InvalidAccountingPeriod =>
              Future.successful(
                UnprocessableEntity(
                  Json.obj(
                    "errors" -> Json.obj(
                      "code" -> "003",
                      "text" -> s"Accounting period (${e.submittedStart} to ${e.submittedEnd}) does not match the registered period (${e.registeredStart} to ${e.registeredEnd})"
                    )
                  )
                )
              )
            case _ =>
              Future.successful(
                InternalServerError(
                  Json.obj(
                    "error" -> Json.obj(
                      "code"    -> INTERNAL_SERVER_ERROR_500,
                      "message" -> "Internal server error",
                      "logID"   -> "C0000000000000000000000000000500"
                    )
                  )
                )
              )
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
