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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendUKTRController @Inject() (
  cc:                  ControllerComponents,
  authActionFilter:    AuthActionFilter,
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

  def amendUKTR: Action[JsValue] = (Action andThen authActionFilter).async(parse.json) { implicit request =>
    logger.info("UKTR amendment request received")
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        if (pillar2Id == ServerErrorPlrId) {
          logger.error("Error triggered by special PLR ID")
          Future.failed(ETMPInternalServerError)
        } else {
          retrieveSubscription(pillar2Id)._2 match {
            case _: SubscriptionSuccessResponse =>
              logger.info(s"Valid subscription found for PLR: $pillar2Id")
              validateRequest(pillar2Id, request)
            case _ =>
              logger.warn(s"Subscription not found for pillar2Id: $pillar2Id")
              Future.failed(RequestCouldNotBeProcessed)
          }
        }
      }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    logger.info(s"Validating amendment request for PLR: $plrReference")

    repository.findByPillar2Id(plrReference).flatMap {
      case Some(_) =>
        logger.info(s"Existing submission found for PLR: $plrReference, proceeding with validation")
        processValidRequest(plrReference, request)
      case None =>
        logger.warn(s"No existing submission found to amend for pillar2Id: $plrReference")
        Future.failed(RequestCouldNotBeProcessed)
    }
  }

  // private def validateAccountingPeriod[T <: UKTRSubmission](submission: T, plrReference: String): Future[T] =
  //   organisationService
  //     .getOrganisation(plrReference)
  //     .flatMap { org =>
  //       logger.debug(s"Retrieved organisation for PLR: $plrReference")
  //       if (
  //         org.organisation.accountingPeriod.startDate.isEqual(submission.accountingPeriodFrom) &&
  //         org.organisation.accountingPeriod.endDate.isEqual(submission.accountingPeriodTo)
  //       ) {
  //         logger.debug(s"Accounting period validated for PLR: $plrReference")

  //         if (org.organisation.orgDetails.domesticOnly) {
  //           logger.debug(s"Organisation is domestic-only for PLR: $plrReference")
  //           submission match {
  //             case liability: UKTRLiabilityReturn =>
  //               if (liability.obligationMTT) {
  //                 logger.warn(s"Invalid obligationMTT=true for domestic-only group, PLR: $plrReference")
  //                 Future.failed(InvalidReturn)
  //               } else if (liability.liabilities.liableEntities.isEmpty) {
  //                 logger.warn(s"Empty liableEntities for liability return, PLR: $plrReference")
  //                 Future.failed(InvalidReturn)
  //               } else {
  //                 Future.successful(submission)
  //               }
  //             case nilReturn: UKTRNilReturn =>
  //               if (nilReturn.obligationMTT) {
  //                 logger.warn(s"Invalid obligationMTT=true for domestic-only group, PLR: $plrReference")
  //                 Future.failed(InvalidReturn)
  //               } else {
  //                 Future.successful(submission)
  //               }
  //             case _ =>
  //               Future.successful(submission)
  //           }
  //         } else {
  //           logger.debug(s"Organisation is not domestic-only for PLR: $plrReference")
  //           submission match {
  //             case liability: UKTRLiabilityReturn =>
  //               if (liability.liabilities.liableEntities.isEmpty) {
  //                 logger.warn(s"Empty liableEntities for liability return, PLR: $plrReference")
  //                 Future.failed(InvalidReturn)
  //               } else {
  //                 Future.successful(submission)
  //               }
  //             case _ =>
  //               Future.successful(submission)
  //           }
  //         }
  //       } else {
  //         logger.warn(
  //           s"Accounting period mismatch for PLR: $plrReference. " +
  //             s"Submitted: ${submission.accountingPeriodFrom} to ${submission.accountingPeriodTo}, " +
  //             s"Expected: ${org.organisation.accountingPeriod.startDate} to ${org.organisation.accountingPeriod.endDate}"
  //         )
  //         Future.failed(InvalidReturn)
  //       }
  //     }

  private def processValidRequest(plrReference: String, request: Request[JsValue])(implicit
    ec:                                         ExecutionContext
  ): Future[Result] = {
    logger.info(s"Processing valid amendment request for PLR: $plrReference")

    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        logger.info(s"Processing liability return amendment for PLR: $plrReference")
        import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn._
        (for {
          validator           <- uktrSubmissionValidator(plrReference)(organisationService, ec)
          validatedSubmission <- validateAccountingPeriod(uktrRequest, plrReference)
          validationResult = validator.validate(validatedSubmission)
          result <- validationResult.toEither match {
                      case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                      case Right(_) =>
                        repository.update(validatedSubmission, plrReference).flatMap {
                          case true =>
                            logger.info(s"Liability return successfully amended for PLR: $plrReference")
                            Future.successful(Ok(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
                          case false =>
                            logger.error(s"Failed to update liability return for PLR: $plrReference")
                            Future.failed(ETMPInternalServerError)
                        }
                    }
        } yield result).recoverWith { case e: Exception =>
          logger.error(s"Error validating request: ${e.getMessage}", e)
          Future.failed(ETMPInternalServerError)
        }

      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        logger.info(s"Processing nil return amendment for PLR: $plrReference")
        import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRNilReturn._
        (for {
          validator           <- uktrNilReturnValidator(plrReference)(organisationService, ec)
          validatedSubmission <- validateAccountingPeriod(nilReturnRequest, plrReference)
          validationResult = validator.validate(validatedSubmission)
          result <- validationResult.toEither match {
                      case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
                      case Right(_) =>
                        repository.update(validatedSubmission, plrReference).flatMap {
                          case true =>
                            logger.info(s"Nil return successfully amended for PLR: $plrReference")
                            Future.successful(Ok(Json.toJson(successfulNilReturnResponse)))
                          case false =>
                            logger.error(s"Failed to update nil return for PLR: $plrReference")
                            Future.failed(ETMPInternalServerError)
                        }
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
}
