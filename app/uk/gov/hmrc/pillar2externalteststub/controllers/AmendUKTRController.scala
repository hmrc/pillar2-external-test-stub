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

import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendUKTRController @Inject() (
  cc:                               ControllerComponents,
  authActionFilter:                 AuthActionFilter,
  override val repository:          UKTRSubmissionRepository,
  override val organisationService: OrganisationService
)(implicit override val ec:         ExecutionContext)
    extends BackendController(cc)
    with UKTRControllerCommon {

  def amendUKTR: Action[JsValue] = (Action andThen authActionFilter).async(parse.json) { implicit request =>
    logger.info("UKTR amendment request received")

    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap(checkForServerErrorId)
      .flatMap { pillar2Id =>
        organisationService.getOrganisation(pillar2Id).flatMap { org =>
          checkExistingSubmission(pillar2Id, request)
        }
      }
  }

  private def checkExistingSubmission(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    logger.info(s"Checking for existing submission for PLR: $plrReference")

    repository.findByPillar2Id(plrReference).flatMap {
      case Some(_) =>
        logger.info(s"Existing submission found for PLR: $plrReference, proceeding with validation")
        processAmendment(plrReference, request)
      case None =>
        logger.warn(s"No existing submission found to amend for pillar2Id: $plrReference")
        Future.failed(RequestCouldNotBeProcessed)
    }
  }

  private def processAmendment(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    // Define the success action for amendment
    val successAction: (UKTRSubmission, String) => Future[Result] = (submission, plrRef) =>
      submission match {
        case nilReturn: UKTRNilReturn =>
          repository.update(nilReturn, plrRef).map(_ => Ok(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
        case liability: UKTRLiabilityReturn =>
          repository.update(liability, plrRef).map(_ => Ok(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)))
        case _ =>
          Future.failed(ETMPBadRequest)
      }

    // Use the common validation and processing logic
    processUKTRSubmission(plrReference, request, successAction)
  }
}
