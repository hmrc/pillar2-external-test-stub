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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{ServerErrorPlrId, pillar2Regex}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNSuccessResponse.{ORN_SUCCESS_200, ORN_SUCCESS_201}
import uk.gov.hmrc.pillar2externalteststub.repositories.ORNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ORNController @Inject() (
  cc:                  ControllerComponents,
  authFilter:          AuthActionFilter,
  repository:          ORNSubmissionRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitORN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    (for {
      pillar2Id <- validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      _         <- checkForServerErrorId(pillar2Id)
      _         <- organisationService.getOrganisation(pillar2Id)
      result <- request.body
                  .validate[ORNRequest]
                  .fold(
                    _ => Future.failed(ETMPBadRequest),
                    ornRequest => handleSubmission(pillar2Id, ornRequest, isCreate = true)
                  )
    } yield result).recoverWith {
      case OrganisationNotFound(_) => Future.failed(NoActiveSubscription)
      case e: ETMPError =>
        logger.error(s"Error validating organisation: ${e.getMessage}", e)
        Future.failed(e)
        
    }
  }

  def amendORN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    (for {
      pillar2Id <- validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      _        <- checkForServerErrorId(pillar2Id)
      _         <- organisationService.getOrganisation(pillar2Id)
      result <- request.body
                  .validate[ORNRequest]
                  .fold(
                    _ => Future.failed(ETMPBadRequest),
                    ornRequest => handleSubmission(pillar2Id, ornRequest, isCreate = false)
                  )
    } yield result).recoverWith {
      case OrganisationNotFound(_) => Future.failed(NoActiveSubscription)
      case e: ETMPError => Future.failed(e)
      case e: Exception =>
        logger.error(s"Unexpected error in amendORN: ${e.getMessage}", e)
        Future.failed(ETMPInternalServerError)
    }
  }

  private def validatePillar2Id(pillar2Id: Option[String]): Future[String] =
    pillar2Id match {
      case Some(id) if pillar2Regex.matches(id) =>
        logger.info(s"Valid Pillar2Id received: $id")
        Future.successful(id)
      case other =>
        logger.warn(s"Invalid Pillar2Id received: $other")
        Future.failed(Pillar2IdMissing)
    }

  private def checkForServerErrorId(pillar2Id: String): Future[String] =
    if (pillar2Id == ServerErrorPlrId) {
      logger.warn("Server error triggered by special PLR ID")
      Future.failed(ETMPInternalServerError)
    } else {
      Future.successful(pillar2Id)
    }

  private def handleSubmission(pillar2Id: String, request: ORNRequest, isCreate: Boolean): Future[Result] = {
    val successResponse  = if (isCreate) ORN_SUCCESS_201 else ORN_SUCCESS_200
    val formBundleNumber = successResponse.success.formBundleNumber

    repository
      .insert(pillar2Id, request, formBundleNumber)
      .map { _ =>
        if (isCreate) Created(Json.toJson(successResponse)) else Ok(Json.toJson(successResponse))
      }
      .recoverWith { case e: Exception =>
        logger.error(s"Database error in handleSubmission: ${e.getMessage}", e)
        Future.failed(ETMPInternalServerError)
      }
  }
}
