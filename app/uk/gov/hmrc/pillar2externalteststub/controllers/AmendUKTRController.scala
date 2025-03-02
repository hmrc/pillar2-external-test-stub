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
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendUKTRController @Inject() (
  authFilter:                        AuthActionFilter,
  repository:                        UKTRSubmissionRepository,
  organisationService:               OrganisationService,
  override val controllerComponents: ControllerComponents
)(implicit ec:                       ExecutionContext)
    extends BackendController(controllerComponents)
    with Logging {

  private def validatePillar2Id(pillar2Id: Option[String]): Future[String] =
    pillar2Id match {
      case Some(id) if pillar2Regex.matches(id) => Future.successful(id)
      case other                                => Future.failed(InvalidPillar2Id(other))
    }

  def amendUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { plr =>
        if (plr == ServerErrorPlrId) {
          logger.info(s"Server error triggered for PLR ID: $plr")
          Future.successful(InternalServerError(Json.toJson(UKTRSimpleError.SAPError)))
        } else {
          retrieveSubscription(plr)._2 match {
            case _: SubscriptionSuccessResponse => validateRequest(plr, request)
            case _ =>
              logger.info(s"Subscription not found for PLR ID: $plr")
              val error = UKTRDetailedError.SubscriptionNotFound(plr)
              Future.successful(UnprocessableEntity(Json.obj("errors" -> error)))
          }
        }
      }
      .recover {
        case _: InvalidPillar2Id =>
          logger.info("Invalid PLR ID format provided in request header")
          val error = UKTRDetailedError.MissingPLRReference
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: SubmissionNotFoundError =>
          logger.info(s"Submission not found: ${e.getMessage}")
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
            text = "Request could not be processed"
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: InvalidAccountingPeriod =>
          logger.info(s"Invalid accounting period: ${e.getMessage}")
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
            text = e.getMessage
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: DomesticOnlyMTTError =>
          logger.info(s"Domestic-only MTT validation failed: ${e.getMessage}")
          val error = UKTRDetailedError(
            processingDate = nowZonedDateTime,
            code = UKTRErrorCodes.INVALID_RETURN_093,
            text = "obligationMTT cannot be true for a domestic-only group"
          )
          UnprocessableEntity(Json.obj("errors" -> error))
        case e: DatabaseError =>
          if (e.getMessage == "Failed to get organisation") {
            logger.error(s"Database error when getting organisation: ${e.getMessage}")
            val error = UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Request could not be processed"
            )
            UnprocessableEntity(Json.obj("errors" -> error))
          } else {
            logger.error(s"Database error: ${e.getMessage}")
            InternalServerError(
              Json.obj(
                "code"    -> "DATABASE_ERROR",
                "message" -> e.getMessage
              )
            )
          }
        case e: RuntimeException =>
          logger.error(s"Runtime exception: ${e.getMessage}", e)
          InternalServerError(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "500",
                "message" -> "Internal server error",
                "logID"   -> "C0000000000000000000000000000500"
              )
            )
          )
        case e: Throwable =>
          logger.error(s"Unhandled exception: ${e.getMessage}", e)
          InternalServerError(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "500",
                "message" -> "Internal server error",
                "logID"   -> "C0000000000000000000000000000500"
              )
            )
          )
      }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    implicit val os = organisationService

    repository.findByPillar2Id(plrReference).flatMap {
      case None =>
        logger.info(s"No existing submission found for PLR ID: $plrReference")
        Future.failed(SubmissionNotFoundError(s"No submission found for pillar2Id: $plrReference"))

      case Some(_) =>
        if ((request.body \ "nilReturn").asOpt[Boolean].contains(true)) {
          // Handle simplified nil return format
          val startDate = (request.body \ "accountingPeriod" \ "startDate").asOpt[String]
          val endDate   = (request.body \ "accountingPeriod" \ "endDate").asOpt[String]

          if (startDate.isEmpty || endDate.isEmpty) {
            Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError("Missing accounting period dates"))))
          } else {
            try {
              val from = LocalDate.parse(startDate.get)
              val to   = LocalDate.parse(endDate.get)

              val nilReturn = UKTRNilReturn(
                accountingPeriodFrom = from,
                accountingPeriodTo = to,
                obligationMTT = false,
                electionUKGAAP = false,
                returnType = "NIL_RETURN",
                liabilities = LiabilityNilReturn(ReturnType.NIL_RETURN)
              )

              validateAccountingPeriod(nilReturn, plrReference)(request)
                .flatMap { _ =>
                  repository.update(nilReturn, plrReference).map {
                    case Right(_) => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                    case Left(_)  => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                  }
                }
                .recoverWith {
                  case e: DatabaseError           => Future.failed(e)
                  case e: InvalidAccountingPeriod => Future.failed(e)
                  case e: OrganisationNotFound    => Future.failed(e)
                  case e: DomesticOnlyMTTError    => Future.failed(e)
                  case e: RuntimeException        => Future.failed(e)
                  case _: Exception               => Future.failed(DatabaseError("Internal server error"))
                }
            } catch {
              case _: Exception =>
                Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError("Invalid date format"))))
            }
          }
        } else if ((request.body \ "customField").isDefined) {

          val startDate = (request.body \ "accountingPeriod" \ "startDate").asOpt[String]
          val endDate   = (request.body \ "accountingPeriod" \ "endDate").asOpt[String]

          if (startDate.isEmpty || endDate.isEmpty) {
            Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError("Missing accounting period dates"))))
          } else {
            try {
              val from = LocalDate.parse(startDate.get)
              val to   = LocalDate.parse(endDate.get)

              val customSubmission = UKTRNilReturn(
                accountingPeriodFrom = from,
                accountingPeriodTo = to,
                obligationMTT = false,
                electionUKGAAP = false,
                returnType = "NIL_RETURN",
                liabilities = LiabilityNilReturn(ReturnType.NIL_RETURN)
              )

              validateAccountingPeriod(customSubmission, plrReference)(request)
                .flatMap { _ =>
                  repository.update(customSubmission, plrReference).map {
                    case Right(_) => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                    case Left(_)  => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                  }
                }
                .recoverWith {
                  case e: DatabaseError           => Future.failed(e)
                  case e: InvalidAccountingPeriod => Future.failed(e)
                  case e: OrganisationNotFound    => Future.failed(e)
                  case e: RuntimeException        => Future.failed(e)
                  case _: Exception               => Future.failed(DatabaseError("Internal server error"))
                }
            } catch {
              case _: Exception =>
                Future.successful(BadRequest(Json.toJson(UKTRSimpleError.InvalidJsonError("Invalid date format"))))
            }
          }
        } else {

          request.body.validate[UKTRSubmission] match {
            case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
              val obligationMTT = (request.body \ "liabilities" \ "obligationMTT").asOpt[Boolean].getOrElse(false)

              if (obligationMTT) {
                val isDomesticOnlyGroup = (request.body \ "liabilities" \ "domesticOnlyGroup").asOpt[Boolean].getOrElse(false)

                if (isDomesticOnlyGroup) {

                  Future.successful(
                    UnprocessableEntity(
                      Json.obj(
                        "errors" -> UKTRDetailedError(
                          processingDate = nowZonedDateTime,
                          code = UKTRErrorCodes.INVALID_RETURN_093,
                          text = "obligationMTT cannot be true for a domestic-only group"
                        )
                      )
                    )
                  )
                } else {

                  val liableEntities = (request.body \ "liabilities" \ "liableEntities").asOpt[Seq[JsValue]].getOrElse(Seq.empty)
                  val allForeignEntitiesFalse = liableEntities.nonEmpty &&
                    liableEntities.forall(entity => (entity \ "hasForeignEntities").asOpt[Boolean].contains(false))

                  if (allForeignEntitiesFalse) {

                    Future.successful(
                      UnprocessableEntity(
                        Json.obj(
                          "errors" -> UKTRDetailedError(
                            processingDate = nowZonedDateTime,
                            code = UKTRErrorCodes.INVALID_RETURN_093,
                            text = "obligationMTT cannot be true for a domestic-only group"
                          )
                        )
                      )
                    )
                  } else {

                    validateAccountingPeriod(uktrRequest, plrReference)(request)
                      .flatMap { submission =>
                        UKTRLiabilityReturn.uktrSubmissionValidator(plrReference).flatMap { validator =>
                          Future.successful(validator.validate(submission).toEither).flatMap {
                            case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                            case Right(_) =>
                              repository.update(submission, plrReference).map {
                                case Right(_) => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                                case Left(_)  => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                              }
                          }
                        }
                      }
                      .recoverWith {
                        case e: DatabaseError           => Future.failed(e)
                        case e: InvalidAccountingPeriod => Future.failed(e)
                        case e: OrganisationNotFound    => Future.failed(e)
                        case e: DomesticOnlyMTTError    => Future.failed(e)
                        case e: RuntimeException        => Future.failed(e)
                        case _: Exception               => Future.failed(DatabaseError("Internal server error"))
                      }
                  }
                }
              } else {

                validateAccountingPeriod(uktrRequest, plrReference)(request)
                  .flatMap { submission =>
                    UKTRLiabilityReturn.uktrSubmissionValidator(plrReference).flatMap { validator =>
                      Future.successful(validator.validate(submission).toEither).flatMap {
                        case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                        case Right(_) =>
                          repository.update(submission, plrReference).map {
                            case Right(_) => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                            case Left(_)  => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                          }
                      }
                    }
                  }
                  .recoverWith {
                    case e: DatabaseError           => Future.failed(e)
                    case e: InvalidAccountingPeriod => Future.failed(e)
                    case e: OrganisationNotFound    => Future.failed(e)
                    case e: DomesticOnlyMTTError    => Future.failed(e)
                    case e: RuntimeException        => Future.failed(e)
                    case _: Exception               => Future.failed(DatabaseError("Internal server error"))
                  }
              }

            case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
              validateAccountingPeriod(nilReturnRequest, plrReference)(request)
                .flatMap { submission =>
                  UKTRNilReturn.uktrNilReturnValidator(plrReference).flatMap { validator =>
                    Future.successful(validator.validate(submission).toEither).flatMap {
                      case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                      case Right(_) =>
                        repository.update(submission, plrReference).map {

                          case Right(_) if (request.body \ "returnType").isDefined =>
                            Created(Json.toJson(NilReturnSuccess.successfulNilReturnResponse))
                          case Right(_) =>
                            Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                          case Left(_) =>
                            InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                        }
                    }
                  }
                }
                .recoverWith {
                  case e: DatabaseError           => Future.failed(e)
                  case e: InvalidAccountingPeriod => Future.failed(e)
                  case e: OrganisationNotFound    => Future.failed(e)
                  case e: DomesticOnlyMTTError    => Future.failed(e)
                  case e: RuntimeException        => Future.failed(e)
                  case _: Exception               => Future.failed(DatabaseError("Internal server error"))
                }

            case JsSuccess(submission, _) =>
              validateAccountingPeriod(submission, plrReference)(request)
                .flatMap { _ =>
                  repository.update(submission, plrReference).map {
                    case Right(_) => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
                    case Left(_)  => InternalServerError(Json.toJson(UKTRSimpleError.SAPError))
                  }
                }
                .recoverWith {
                  case e: DatabaseError           => Future.failed(e)
                  case e: InvalidAccountingPeriod => Future.failed(e)
                  case e: OrganisationNotFound    => Future.failed(e)
                  case e: DomesticOnlyMTTError    => Future.failed(e)
                  case e: RuntimeException        => Future.failed(e)
                  case _: Exception               => Future.failed(DatabaseError("Internal server error"))
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
          }
        }
    }
  }

  def validateAccountingPeriod[T <: UKTRSubmission](submission: T, plrReference: String)(implicit request: Request[JsValue]): Future[T] = {
    logger.info(s"Processing UKTR submission for PLR ID: $plrReference")

    organisationService
      .getOrganisation(plrReference)
      .flatMap { org =>
        if (
          org.organisation.accountingPeriod.startDate.isEqual(submission.accountingPeriodFrom) &&
          org.organisation.accountingPeriod.endDate.isEqual(submission.accountingPeriodTo)
        ) {
          if (org.organisation.orgDetails.domesticOnly) {
            submission match {
              case liability: UKTRLiabilityReturn =>
                if (liability.obligationMTT) {
                  logger.info(s"Validation error: obligationMTT=true for domestic-only organization, PLR ID: $plrReference")
                  Future.failed(DomesticOnlyMTTError(plrReference))
                } else {
                  validateLiabilityReturn(liability)(request).asInstanceOf[Future[T]]
                }
              case nilReturn: UKTRNilReturn =>
                if (nilReturn.obligationMTT) {
                  logger.info(s"Validation error: obligationMTT=true for domestic-only organization nil return, PLR ID: $plrReference")
                  Future.failed(DomesticOnlyMTTError(plrReference))
                } else {
                  Future.successful(submission)
                }
              case _ =>
                Future.successful(submission)
            }
          } else {

            val obligationMTT = (request.body \ "obligationMTT")
              .asOpt[Boolean]
              .orElse(
                (request.body \ "liabilities" \ "obligationMTT").asOpt[Boolean]
              )
              .getOrElse(false)

            val isDomesticOnlyGroup = (request.body \ "liabilities" \ "domesticOnlyGroup").asOpt[Boolean].getOrElse(false)

            if (obligationMTT && isDomesticOnlyGroup) {
              logger.info(s"Validation error: obligationMTT=true and domesticOnlyGroup=true, PLR ID: $plrReference")
              Future.failed(DomesticOnlyMTTError(plrReference))
            } else if (obligationMTT && !isDomesticOnlyGroup) {

              val liableEntities = (request.body \ "liabilities" \ "liableEntities").asOpt[Seq[JsValue]].getOrElse(Seq.empty)

              val allForeignEntitiesFalse = liableEntities.nonEmpty &&
                liableEntities.forall { entity =>
                  (entity \ "hasForeignEntities").asOpt[Boolean].contains(false)
                }

              if (allForeignEntitiesFalse) {
                logger.info(s"Validation error: obligationMTT=true with all entities having hasForeignEntities=false, PLR ID: $plrReference")
                Future.failed(DomesticOnlyMTTError(plrReference))
              } else {
                submission match {
                  case liability: UKTRLiabilityReturn => validateLiabilityReturn(liability)(request).asInstanceOf[Future[T]]
                  case _ => Future.successful(submission)
                }
              }
            } else {
              submission match {
                case liability: UKTRLiabilityReturn => validateLiabilityReturn(liability)(request).asInstanceOf[Future[T]]
                case _ => Future.successful(submission)
              }
            }
          }
        } else {
          val registeredStart = org.organisation.accountingPeriod.startDate.toString
          val registeredEnd   = org.organisation.accountingPeriod.endDate.toString
          val submittedStart  = submission.accountingPeriodFrom.toString
          val submittedEnd    = submission.accountingPeriodTo.toString

          logger.info(
            s"Accounting period validation failed: submitted=[$submittedStart to $submittedEnd], registered=[$registeredStart to $registeredEnd], PLR ID: $plrReference"
          )

          Future.failed(
            InvalidAccountingPeriod(
              submittedStart = submittedStart,
              submittedEnd = submittedEnd,
              registeredStart = registeredStart,
              registeredEnd = registeredEnd
            )
          )
        }
      }
      .recoverWith {
        case e: StubError =>
          logger.warn(s"StubError during validation: ${e.getMessage}, PLR ID: $plrReference")
          Future.failed(e)
        case e: RuntimeException =>
          logger.error(s"Runtime exception during validation: ${e.getMessage}, PLR ID: $plrReference", e)
          Future.failed(DatabaseError("Failed to get organisation"))
        case e: Exception =>
          logger.error(s"Exception during validation: ${e.getMessage}, PLR ID: $plrReference", e)
          Future.failed(
            DatabaseError("Failed to get organisation")
          )
      }
  }

  private def validateLiabilityReturn(submission: UKTRLiabilityReturn)(implicit request: Request[JsValue]): Future[UKTRLiabilityReturn] =
    if (submission.liabilities.liableEntities.isEmpty) {
      logger.info(s"Validation error: liableEntities array is empty")
      Future.failed(
        DomesticOnlyMTTError("liableEntities array cannot be empty")
      )
    } else {
      Future.successful(submission)
    }
}
