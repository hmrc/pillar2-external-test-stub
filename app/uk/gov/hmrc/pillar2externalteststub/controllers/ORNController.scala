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
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{ServerErrorPlrId, pillar2Regex}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNSuccessResponse.{ORN_SUCCESS_200, ORN_SUCCESS_201}
import uk.gov.hmrc.pillar2externalteststub.models.orn.{ORNGetResponse, ORNRequest}
import uk.gov.hmrc.pillar2externalteststub.repositories.ORNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.{ORNService, OrganisationService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ORNController @Inject() (
  cc:                  ControllerComponents,
  authFilter:          AuthActionFilter,
  ornService:          ORNService,
  repository:          ORNSubmissionRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitORN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")).flatMap(checkForServerErrorId).flatMap { pillar2Id =>
      request.body
        .validate[ORNRequest]
        .fold(
          _ => Future.failed(ETMPBadRequest),
          ornRequest =>
            organisationService
              .getOrganisation(pillar2Id)
              .flatMap(_ => ornService.submitORN(pillar2Id, ornRequest))
              .map(_ => Created(Json.toJson(ORN_SUCCESS_201)))
              .recoverWith { case _: OrganisationNotFound =>
                Future.failed(NoActiveSubscription)
              }
        )
    }
  }

  def amendORN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")).flatMap(checkForServerErrorId).flatMap { pillar2Id =>
      request.body
        .validate[ORNRequest]
        .fold(
          _ => Future.failed(ETMPBadRequest),
          ornRequest =>
            organisationService
              .getOrganisation(pillar2Id)
              .flatMap(_ => ornService.amendORN(pillar2Id, ornRequest))
              .map(_ => Ok(Json.toJson(ORN_SUCCESS_200)))
              .recoverWith { case _: OrganisationNotFound =>
                Future.failed(NoActiveSubscription)
              }
        )
    }
  }

  def getORN(accountingPeriodFrom: String, accountingPeriodTo: String): Action[AnyContent] = (Action andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")).flatMap(checkForServerErrorId).flatMap { pillar2Id =>
      try {
        val fromDate = LocalDate.parse(accountingPeriodFrom)
        val toDate   = LocalDate.parse(accountingPeriodTo)

        ornService
          .getORN(pillar2Id, fromDate, toDate)
          .flatMap {
            case Some(submission) => Future.successful(Ok(Json.toJson(ORNGetResponse.fromSubmission(submission))))
            case None             => Future.failed(RequestCouldNotBeProcessed)
          }
          .recoverWith { case _: OrganisationNotFound =>
            Future.failed(NoActiveSubscription)
          }
      } catch {
        case e: DateTimeParseException =>
          logger.error(s"Invalid date format: ${e.getMessage}")
          Future.failed(ETMPBadRequest)
      }
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
}
