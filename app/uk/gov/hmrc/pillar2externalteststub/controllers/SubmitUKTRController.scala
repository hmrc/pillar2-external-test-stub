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
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.{ObligationsAndSubmissionsRepository, UKTRSubmissionRepository}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitUKTRController @Inject() (
  cc:                               ControllerComponents,
  authFilter:                       AuthActionFilter,
  override val uktrRepository:      UKTRSubmissionRepository,
  override val organisationService: OrganisationService,
  oasRepository:                    ObligationsAndSubmissionsRepository
)(implicit override val ec:         ExecutionContext)
    extends BackendController(cc)
    with UKTRControllerCommon {

  def submitUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    logger.info("UKTR submission request received")

    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        organisationService
          .getOrganisation(pillar2Id)
          .flatMap { _ =>
            request.body.validate[UKTRSubmission] match {
              case JsSuccess(_, _) =>
                processSubmission(pillar2Id, request)
              case JsError(_) => Future.failed(ETMPBadRequest)
            }
          }
          .recoverWith {
            case OrganisationNotFound(_) =>
              logger.warn(s"Organisation not found for pillar2Id: $pillar2Id")
              Future.failed(NoActiveSubscription)

            case e: Exception =>
              logger.error(s"Error validating organisation: ${e.getMessage}", e)
              Future.failed(e)
          }
      }
  }

  private def processSubmission(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] = {
    // Define the success action for submission
    val successAction: (UKTRSubmission, String) => Future[Result] = (submission, plrRef) =>
      submission match {
        case nilReturn: UKTRNilReturn =>
          uktrRepository.insert(nilReturn, plrRef).flatMap { sub =>
            oasRepository.insert(nilReturn, plrRef, sub).map { _ =>
              Created(Json.toJson(NilReturnSuccess.successfulNilReturnResponse))
            }
          }
        case liability: UKTRLiabilityReturn =>
          uktrRepository.insert(liability, plrRef).flatMap { sub =>
            oasRepository.insert(liability, plrRef, sub).map { _ =>
              Created(Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse))
            }
          }
        case _ =>
          Future.failed(ETMPBadRequest)
      }

    // Use the common validation and processing logic
    processUKTRSubmission(plrReference, request, successAction)
  }
}
