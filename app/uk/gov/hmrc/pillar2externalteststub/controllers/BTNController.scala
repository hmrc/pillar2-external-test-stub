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
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNErrorResponse.{BTN_ERROR_400, BTN_ERROR_500}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNFailureResponse._
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNSuccessResponse.BTN_SUCCESS_201
import uk.gov.hmrc.pillar2externalteststub.models.btn._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.util.{Failure, Success, Try}

class BTNController @Inject() (cc: ControllerComponents, authFilter: AuthActionFilter) extends BackendController(cc) with Logging {

  def submitBTN: Action[String] = (Action(parse.tolerantText) andThen authFilter) { implicit request =>
    request.headers.get("X-Pillar2-Id") match {
      case Some(plrReference) =>
        plrReference match {
          case "XEPLR0000000201" => Created(Json.toJson(BTN_SUCCESS_201))
          case "XEPLR0000000400" => BadRequest(Json.toJson(BTN_ERROR_400(message = "Invalid JSON payload error")))
          case "XEPLR0000000422" => UnprocessableEntity(Json.toJson(BTN_GENERIC_422))
          case "XEPLR0000000500" => InternalServerError(Json.toJson(BTN_ERROR_500(message = "SAP system failure: ...")))
          case _ =>
            Try(Json.parse(request.body)) match {
              case Success(json) =>
                json.validate[BTNRequest] match {
                  case JsSuccess(btn: BTNRequest, _) =>
                    if (!btn.accountingPeriodValid) UnprocessableEntity(Json.toJson(BTN_REQUEST_INVALID_003))
                    else Created(Json.toJson(BTN_SUCCESS_201))
                  case JsError(error) => BadRequest(Json.toJson(BTN_ERROR_400(error.map(_._2).toString)))
                }
              case Failure(exception) => BadRequest(Json.toJson(BTN_ERROR_400(exception.getLocalizedMessage)))

            }
        }
      case None => UnprocessableEntity(Json.toJson(BTN_PILLAR2_MISSING_002))
    }
  }
}
