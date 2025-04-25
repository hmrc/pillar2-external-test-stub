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
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNSuccessResponse.{ORN_SUCCESS_200, ORN_SUCCESS_201}
import uk.gov.hmrc.pillar2externalteststub.models.orn._
import uk.gov.hmrc.pillar2externalteststub.services.{ORNService, OrganisationService}
import uk.gov.hmrc.pillar2externalteststub.validation.syntax._
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
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitORN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        request.body
          .validate[ORNRequest]
          .fold(
            _ => Future.failed(ETMPBadRequest),
            ornRequest => validateORN(pillar2Id, ornRequest, isAmendment = false)
          )
      }
  }

  def amendORN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        request.body
          .validate[ORNRequest]
          .fold(
            _ => Future.failed(ETMPBadRequest),
            ornRequest => validateORN(pillar2Id, ornRequest, isAmendment = true)
          )
      }
  }

  def getORN(accountingPeriodFrom: String, accountingPeriodTo: String): Action[AnyContent] = (Action andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        try {
          val fromDate = LocalDate.parse(accountingPeriodFrom)
          val toDate   = LocalDate.parse(accountingPeriodTo)

          ornService
            .getORN(pillar2Id, fromDate, toDate)
            .flatMap {
              case Some(submission) => Future.successful(Ok(Json.toJson(ORNGetResponse.fromSubmission(submission))))
              case None             => Future.failed(NoFormBundleFound)
            }
        } catch {
          case e: DateTimeParseException =>
            logger.error(s"Invalid date format: ${e.getMessage}")
            Future.failed(ETMPBadRequest)
        }
      }
  }

  private def validateORN(pillar2Id: String, request: ORNRequest, isAmendment: Boolean): Future[Result] = {
    logger.info(s"Validating ORN submission for pillar2Id: $pillar2Id")

    ORNValidator.ornValidator(pillar2Id)(organisationService, ec).flatMap { validator =>
      val validationResult = request.validate(validator)

      validationResult.toEither match {
        case Left(errors) =>
          errors.head match {
            case ORNValidationError(error) => Future.failed(error)
            case _                         => Future.failed(ETMPInternalServerError)
          }
        case Right(_) =>
          logger.info(s"ORN validation succeeded for pillar2Id: $pillar2Id")
          if (isAmendment) {
            ornService
              .amendORN(pillar2Id, request)
              .map(_ => Ok(Json.toJson(ORN_SUCCESS_200)))
          } else {
            ornService
              .submitORN(pillar2Id, request)
              .map(_ => Created(Json.toJson(ORN_SUCCESS_201)))
          }
      }
    }
  }
}
