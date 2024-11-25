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
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.InvalidJsonError400
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.SAPError500
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.ValidationError422
import uk.gov.hmrc.pillar2externalteststub.models.uktr.repsonse.ErrorResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.repsonse.SubmitUKTRSuccessResponse
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class SubmitUKTRController @Inject() (
  cc:         ControllerComponents,
  authFilter: AuthActionFilter
) extends BackendController(cc)
    with Logging {

  def submitUKTR(plrReference: String): Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    logger.info(s"... Submitting UKTR subscription for PLR reference: $plrReference")

    plrReference match {
      case "XEPLR0000000422" =>
        Future.successful(UnprocessableEntity(Json.toJson(ErrorResponse.detailed(ValidationError422.response))))
      case "XEPLR0000000500" =>
        Future.successful(InternalServerError(Json.toJson(ErrorResponse.simple(SAPError500.response))))
      case "XEPLR0000000400" =>
        Future.successful(BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400.response))))
      case _ =>
        Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
    }
  }
}
