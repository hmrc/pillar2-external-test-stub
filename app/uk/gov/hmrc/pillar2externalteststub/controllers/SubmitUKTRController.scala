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
      .recover {
        case _: InvalidPillar2Id =>
          val error = UKTRDetailedError.MissingPLRReference
          UnprocessableEntity(Json.obj("errors" -> error))
        case _: DuplicateSubmissionError =>
          val error = UKTRDetailedError.DuplicateSubmissionError
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: InvalidAccountingPeriod =>
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
            text = e.getMessage
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case _: DomesticOnlyMTTError =>
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.INVALID_RETURN_093,
            text = "obligationMTT cannot be true for a domestic-only group"
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case _: DatabaseError =>
          val error = UKTRDetailedError.RequestCouldNotBeProcessed
          UnprocessableEntity(Json.obj("errors" -> error))
        case e =>
          logger.error("Unexpected error", e)
          InternalServerError(
            Json.obj(
              "code"    -> "500",
              "message" -> "Internal Server Error"
            )
          )
      }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    implicit val os = organisationService

    retrieveSubscription(plrReference)._2 match {
      case _: SubscriptionSuccessResponse => processValidRequest(plrReference, request)
      case _ =>
        val error = UKTRDetailedError.SubscriptionNotFound(plrReference)
        Future.successful(UnprocessableEntity(Json.obj("errors" -> error)))
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
            // Check if the group is domestic-only and validate MTT values
            if (org.organisation.orgDetails.domesticOnly) {
              submission match {
                case liability: UKTRLiabilityReturn =>
                  if (liability.obligationMTT) {
                    Future.failed(DomesticOnlyMTTError(plrReference))
                  } else {
                    Future.successful(submission)
                  }
                case nilReturn: UKTRNilReturn =>
                  if (nilReturn.obligationMTT) {
                    Future.failed(DomesticOnlyMTTError(plrReference))
                  } else {
                    Future.successful(submission)
                  }
                case _ =>
                  Future.successful(submission)
              }
            } else {
              Future.successful(submission)
            }
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
          case e: DatabaseError           => Future.failed(e)
          case e: OrganisationNotFound    => Future.failed(e)
          case e: InvalidAccountingPeriod => Future.failed(e)
          case e: DomesticOnlyMTTError    => Future.failed(e)
          case _: RuntimeException =>
            Future.failed(DatabaseError("Request could not be processed"))
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
                    case true => Future.failed(DuplicateSubmissionError(plrReference))
                    case false =>
                      repository.insert(submission, plrReference).map(_ => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
                  }
              }
            }
          }
          .recoverWith {
            case e: DatabaseError            => Future.failed(e)
            case e: InvalidAccountingPeriod  => Future.failed(e)
            case e: OrganisationNotFound     => Future.failed(e)
            case e: DuplicateSubmissionError => Future.failed(e)
            case e: DomesticOnlyMTTError     => Future.failed(e)
            case _: RuntimeException =>
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
            case _ => Future.failed(DatabaseError("Internal server error"))
          }

      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        validateAccountingPeriod(nilReturnRequest)
          .flatMap { submission =>
            UKTRNilReturn.uktrNilReturnValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  repository.isDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo).flatMap {
                    case true  => Future.failed(DuplicateSubmissionError(plrReference))
                    case false => repository.insert(submission, plrReference).map(_ => Created(Json.toJson(successfulNilReturnResponse)))
                  }
              }
            }
          }
          .recoverWith {
            case e: DatabaseError            => Future.failed(e)
            case e: InvalidAccountingPeriod  => Future.failed(e)
            case e: OrganisationNotFound     => Future.failed(e)
            case e: DuplicateSubmissionError => Future.failed(e)
            case e: DomesticOnlyMTTError     => Future.failed(e)
            case _: RuntimeException =>
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
            case _ => Future.failed(DatabaseError("Internal server error"))
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
