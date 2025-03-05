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
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import scala.concurrent.{ExecutionContext, Future}

trait UKTRControllerCommon extends Logging {

  protected def repository:          UKTRSubmissionRepository
  protected def organisationService: OrganisationService
  implicit def ec:                   ExecutionContext

  // Common validation for Pillar2Id
  protected def validatePillar2Id(pillar2Id: Option[String]): Future[String] =
    pillar2Id match {
      case Some(id) if pillar2Regex.matches(id) =>
        logger.info(s"Valid Pillar2Id received: $id")
        Future.successful(id)
      case other =>
        logger.warn(s"Invalid Pillar2Id received: $other")
        Future.failed(Pillar2IdMissing)
    }

  // Check for server error PLR ID
  protected def checkForServerErrorId(pillar2Id: String): Future[String] =
    if (pillar2Id == ServerErrorPlrId) {
      logger.warn("Server error triggered by special PLR ID")
      Future.failed(ETMPInternalServerError)
    } else {
      Future.successful(pillar2Id)
    }

  // Common processing logic for validating UKTRSubmission
  protected def processUKTRSubmission(
    plrReference:  String,
    request:       Request[JsValue],
    successAction: (UKTRSubmission, String) => Future[Result]
  )(implicit ec:   ExecutionContext): Future[Result] = {
    logger.info(s"Processing UKTR submission for PLR: $plrReference")

    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        logger.info(s"Processing liability return for PLR: $plrReference")
        import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn._
        (for {
          validator <- uktrSubmissionValidator(plrReference)(organisationService, ec)
        } yield {
          val validationResult = validator.validate(uktrRequest)
          validationResult.toEither match {
            case Left(errors) =>
              errors.head match {
                case UKTRSubmissionError(error) => Future.failed(error)
                case _                          => Future.failed(ETMPInternalServerError)
              }
            case Right(_) =>
              logger.info(s"UKTR request validated successfully for PLR: $plrReference")
              successAction(uktrRequest, plrReference)
          }
        }).flatten.recoverWith { case e: Exception =>
          logger.error(s"Error validating request: ${e.getMessage}", e)
          Future.failed(e)
        }

      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        logger.info(s"Processing nil return for PLR: $plrReference")
        import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRNilReturn._
        (for {
          validator <- uktrNilReturnValidator(plrReference)(organisationService, ec)
        } yield {
          val validationResult = validator.validate(nilReturnRequest)
          validationResult.toEither match {
            case Left(errors) =>
              errors.head match {
                case UKTRSubmissionError(error) => Future.failed(error)
                case _                          => Future.failed(ETMPInternalServerError)
              }
            case Right(_) =>
              logger.info(s"Nil return request validated successfully for PLR: $plrReference")
              successAction(nilReturnRequest, plrReference)
          }
        }).flatten.recoverWith { case e: Exception =>
          logger.error(s"Error validating request: ${e.getMessage}", e)
          Future.failed(e)
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
