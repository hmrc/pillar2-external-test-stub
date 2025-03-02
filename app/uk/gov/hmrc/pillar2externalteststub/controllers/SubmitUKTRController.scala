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
      case Some(id) if pillar2Regex.matches(id) =>
        logger.info(s"Valid Pillar2Id received: $id")
        Future.successful(id)
      case other =>
        logger.warn(s"Invalid Pillar2Id received: $other")
        Future.failed(InvalidPillar2Id(other))
    }

  def submitUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    logger.info("UKTR submission request received")
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { plr =>
        if (plr == ServerErrorPlrId) {
          logger.info(s"Server error PLR ID detected: $plr - returning 500 response")
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
        case e: InvalidPillar2Id =>
          logger.warn(s"Missing or invalid PLR reference: ${e.pillar2Id}")
          val error = UKTRDetailedError.MissingPLRReference
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: DuplicateSubmissionError =>
          logger.warn(s"Duplicate submission detected for PLR: ${e.pillar2Id}")
          val error = UKTRDetailedError.DuplicateSubmissionError
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: InvalidAccountingPeriod =>
          logger.warn(s"Invalid accounting period: ${e.getMessage}")
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
            text = e.getMessage
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: DomesticOnlyMTTError =>
          logger.warn(s"Domestic-only MTT error for PLR: ${e.pillar2Id}")
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.INVALID_RETURN_093,
            text = "obligationMTT cannot be true for a domestic-only group"
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: DatabaseError =>
          logger.error(s"Database error: ${e.message}")
          val error = UKTRDetailedError.RequestCouldNotBeProcessed
          UnprocessableEntity(Json.obj("errors" -> error))
        case e =>
          logger.error("Unexpected error during UKTR submission", e)
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
    logger.info(s"Validating request for PLR: $plrReference")

    retrieveSubscription(plrReference)._2 match {
      case _: SubscriptionSuccessResponse =>
        logger.info(s"Subscription found for PLR: $plrReference, proceeding with validation")
        processValidRequest(plrReference, request)
      case _ =>
        logger.warn(s"Subscription not found for PLR: $plrReference")
        val error = UKTRDetailedError.SubscriptionNotFound(plrReference)
        Future.successful(UnprocessableEntity(Json.obj("errors" -> error)))
    }
  }

  private def processValidRequest(plrReference: String, request: Request[JsValue])(implicit
    ec:                                         ExecutionContext,
    os:                                         OrganisationService
  ): Future[Result] = {
    logger.info(s"Processing valid request for PLR: $plrReference")

    def validateAccountingPeriod[T <: UKTRSubmission](submission: T): Future[T] =
      os.getOrganisation(plrReference)
        .flatMap { org =>
          logger.debug(s"Retrieved organisation for PLR: $plrReference")
          if (
            org.organisation.accountingPeriod.startDate.isEqual(submission.accountingPeriodFrom) &&
            org.organisation.accountingPeriod.endDate.isEqual(submission.accountingPeriodTo)
          ) {
            logger.debug(s"Accounting period validated for PLR: $plrReference")

            if (org.organisation.orgDetails.domesticOnly) {
              logger.debug(s"Organisation is domestic-only for PLR: $plrReference")
              submission match {
                case liability: UKTRLiabilityReturn =>
                  if (liability.obligationMTT) {
                    logger.warn(s"Invalid obligationMTT=true for domestic-only group, PLR: $plrReference")
                    Future.failed(DomesticOnlyMTTError(plrReference))
                  } else {
                    Future.successful(submission)
                  }
                case nilReturn: UKTRNilReturn =>
                  if (nilReturn.obligationMTT) {
                    logger.warn(s"Invalid obligationMTT=true for domestic-only group, PLR: $plrReference")
                    Future.failed(DomesticOnlyMTTError(plrReference))
                  } else {
                    Future.successful(submission)
                  }
                case _ =>
                  Future.successful(submission)
              }
            } else {
              logger.debug(s"Organisation is not domestic-only for PLR: $plrReference")
              Future.successful(submission)
            }
          } else {
            logger.warn(
              s"Accounting period mismatch for PLR: $plrReference. " +
                s"Submitted: ${submission.accountingPeriodFrom} to ${submission.accountingPeriodTo}, " +
                s"Expected: ${org.organisation.accountingPeriod.startDate} to ${org.organisation.accountingPeriod.endDate}"
            )
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
          case e: DatabaseError =>
            logger.error(s"Database error when validating accounting period for PLR: $plrReference - ${e.message}")
            Future.failed(e)
          case e: OrganisationNotFound =>
            logger.error(s"Organisation not found for PLR: $plrReference")
            Future.failed(e)
          case e: InvalidAccountingPeriod =>
            logger.warn(s"Invalid accounting period for PLR: $plrReference - ${e.getMessage}")
            Future.failed(e)
          case e: DomesticOnlyMTTError =>
            logger.warn(s"Domestic-only MTT error for PLR: $plrReference")
            Future.failed(e)
          case e: RuntimeException =>
            logger.error(s"Runtime exception when validating accounting period for PLR: $plrReference", e)
            Future.failed(DatabaseError("Request could not be processed"))
          case e =>
            logger.error(s"Unexpected error when validating accounting period for PLR: $plrReference", e)
            Future.failed(DatabaseError("Request could not be processed"))
        }

    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        logger.info(s"Processing liability return for PLR: $plrReference")
        validateAccountingPeriod(uktrRequest)
          .flatMap { submission =>
            UKTRLiabilityReturn.uktrSubmissionValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) =>
                  logger.warn(s"Validation errors for liability return, PLR: $plrReference")
                  UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  logger.debug(s"Liability return validation successful for PLR: $plrReference")
                  repository.isDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo).flatMap {
                    case true =>
                      logger.warn(s"Duplicate submission detected for PLR: $plrReference")
                      Future.failed(DuplicateSubmissionError(plrReference))
                    case false =>
                      logger.info(s"Inserting liability return for PLR: $plrReference")
                      repository.insert(submission, plrReference).map { _ =>
                        logger.info(s"Liability return successfully submitted for PLR: $plrReference")
                        Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                      }
                  }
              }
            }
          }
          .recoverWith {
            case e: DatabaseError =>
              logger.error(s"Database error processing liability return for PLR: $plrReference - ${e.message}")
              Future.failed(e)
            case e: InvalidAccountingPeriod =>
              logger.warn(s"Invalid accounting period for liability return, PLR: $plrReference - ${e.getMessage}")
              Future.failed(e)
            case e: OrganisationNotFound =>
              logger.error(s"Organisation not found for liability return, PLR: $plrReference")
              Future.failed(e)
            case e: DuplicateSubmissionError =>
              logger.warn(s"Duplicate submission for liability return, PLR: $plrReference")
              Future.failed(e)
            case e: DomesticOnlyMTTError =>
              logger.warn(s"Domestic-only MTT error for liability return, PLR: $plrReference")
              Future.failed(e)
            case e: RuntimeException =>
              logger.error(s"Runtime exception processing liability return for PLR: $plrReference", e)
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
            case e =>
              logger.error(s"Unexpected error processing liability return for PLR: $plrReference", e)
              Future.failed(DatabaseError("Internal server error"))
          }

      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        logger.info(s"Processing nil return for PLR: $plrReference")
        validateAccountingPeriod(nilReturnRequest)
          .flatMap { submission =>
            UKTRNilReturn.uktrNilReturnValidator(plrReference).flatMap { validator =>
              Future.successful(validator.validate(submission).toEither).flatMap {
                case Left(errors) =>
                  logger.warn(s"Validation errors for nil return, PLR: $plrReference")
                  UKTRErrorTransformer.from422ToJson(errors)
                case Right(_) =>
                  logger.debug(s"Nil return validation successful for PLR: $plrReference")
                  repository.isDuplicateSubmission(plrReference, submission.accountingPeriodFrom, submission.accountingPeriodTo).flatMap {
                    case true =>
                      logger.warn(s"Duplicate submission detected for nil return, PLR: $plrReference")
                      Future.failed(DuplicateSubmissionError(plrReference))
                    case false =>
                      logger.info(s"Inserting nil return for PLR: $plrReference")
                      repository.insert(submission, plrReference).map { _ =>
                        logger.info(s"Nil return successfully submitted for PLR: $plrReference")
                        Created(Json.toJson(successfulNilReturnResponse))
                      }
                  }
              }
            }
          }
          .recoverWith {
            case e: DatabaseError =>
              logger.error(s"Database error processing nil return for PLR: $plrReference - ${e.message}")
              Future.failed(e)
            case e: InvalidAccountingPeriod =>
              logger.warn(s"Invalid accounting period for nil return, PLR: $plrReference - ${e.getMessage}")
              Future.failed(e)
            case e: OrganisationNotFound =>
              logger.error(s"Organisation not found for nil return, PLR: $plrReference")
              Future.failed(e)
            case e: DuplicateSubmissionError =>
              logger.warn(s"Duplicate submission for nil return, PLR: $plrReference")
              Future.failed(e)
            case e: DomesticOnlyMTTError =>
              logger.warn(s"Domestic-only MTT error for nil return, PLR: $plrReference")
              Future.failed(e)
            case e: RuntimeException =>
              logger.error(s"Runtime exception processing nil return for PLR: $plrReference", e)
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
            case e =>
              logger.error(s"Unexpected error processing nil return for PLR: $plrReference", e)
              Future.failed(DatabaseError("Internal server error"))
          }

      case JsError(errors) =>
        val errorMessage = errors
          .map { case (path, validationErrors) =>
            val fieldName     = path.toJsonString
            val errorMessages = validationErrors.map(_.message).mkString(", ")
            s"Field: $fieldName: $errorMessages"
          }
          .mkString("; ")
        logger.warn(s"Invalid JSON received: $errorMessage")
        Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError(errorMessage))))
      case _ =>
        logger.warn("Unrecognized submission type received")
        Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError())))
    }
  }
}
