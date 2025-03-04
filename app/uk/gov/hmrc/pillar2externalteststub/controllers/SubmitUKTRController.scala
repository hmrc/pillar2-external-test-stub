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
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse
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
        Future.failed(Pillar2IdMissing)
    }

  def submitUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    logger.info("UKTR submission request received")
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        if (pillar2Id == ServerErrorPlrId) {
          logger.warn("Server error triggered by special PLR ID")
          Future.failed(ETMPInternalServerError)
        } else {
          retrieveSubscription(pillar2Id)._2 match {
            case _: SubscriptionSuccessResponse =>
              // Validate the request first
              organisationService
                .getOrganisation(pillar2Id)
                .flatMap { org =>
                  // Check if valid organisation
                  val orgAccountingPeriod = org.organisation.accountingPeriod
                  logger.warn(s"Organization accounting period: ${orgAccountingPeriod.startDate} to ${orgAccountingPeriod.endDate}")

                  request.body.validate[UKTRSubmission] match {
                    case JsSuccess(uktrSubmission, _) =>
                      // Check accounting period matches
                      logger.warn(s"Submission accounting period: ${uktrSubmission.accountingPeriodFrom} to ${uktrSubmission.accountingPeriodTo}")

                      if (
                        uktrSubmission.accountingPeriodFrom.isEqual(orgAccountingPeriod.startDate) &&
                        uktrSubmission.accountingPeriodTo.isEqual(orgAccountingPeriod.endDate)
                      ) {
                        logger.warn("Accounting periods match, proceeding with validation")
                        validateRequest(pillar2Id, request)
                      } else {
                        logger.warn(
                          s"Accounting period mismatch for PLR: $pillar2Id. " +
                            s"Submitted: ${uktrSubmission.accountingPeriodFrom} to ${uktrSubmission.accountingPeriodTo}, " +
                            s"Expected: ${orgAccountingPeriod.startDate} to ${orgAccountingPeriod.endDate}"
                        )
                        Future.failed(
                          InvalidReturn
                        )
                      }
                    case JsError(_) => validateRequest(pillar2Id, request) // Let the validateRequest handle the JSON error
                  }
                }
                .recoverWith { case e: Exception =>
                  logger.error(s"Error validating organisation: ${e.getMessage}", e)
                  Future.failed(ETMPInternalServerError)
                }
            case _ =>
              logger.warn(s"Subscription not found for pillar2Id: $pillar2Id")
              Future.failed(ETMPInternalServerError)
          }
        }
      }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] =
    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn._
        (for {
          validator <- uktrSubmissionValidator(plrReference)(organisationService, ec)
          validationResult = validator.validate(uktrRequest)
          result <- validationResult.toEither match {
                      case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                      case Right(_) =>
                        repository.insert(uktrRequest, plrReference).map(_ => Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
                    }
        } yield result).recoverWith { case e: Exception =>
          logger.error(s"Error validating request: ${e.getMessage}", e)
          Future.failed(ETMPInternalServerError)
        }
      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRNilReturn._
        (for {
          validator <- uktrNilReturnValidator(plrReference)(organisationService, ec)
          validationResult = validator.validate(nilReturnRequest)
          result <- validationResult.toEither match {
                      case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                      case Right(_) =>
                        repository.insert(nilReturnRequest, plrReference).map(_ => Created(Json.toJson(NilReturnSuccess.successfulNilReturnResponse)))
                    }
        } yield result).recoverWith { case e: Exception =>
          logger.error(s"Error validating request: ${e.getMessage}", e)
          Future.failed(ETMPInternalServerError)
        }
      case JsError(errors) =>
        val errorMessage = errors
          .map { case (path, validationErrors) =>
            val fieldName     = path.toJsonString
            val errorMessages = validationErrors.map(_.message).mkString(", ")
            s"Field: $fieldName: $errorMessages"
          }
          .mkString(", ")
        logger.warn(s"JSON validation failed: $errorMessage")
        Future.failed(ETMPBadRequest)
      case JsSuccess(submission: UKTRSubmission, _) =>
        logger.warn(s"Unsupported UKTRSubmission type: ${submission.getClass.getSimpleName}")
        Future.failed(ETMPBadRequest)
    }
}
