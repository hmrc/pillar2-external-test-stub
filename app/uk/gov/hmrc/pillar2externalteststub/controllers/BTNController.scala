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
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.models.btn.*
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNSuccessResponse.BTN_SUCCESS_201
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.*
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest
import uk.gov.hmrc.pillar2externalteststub.services.BTNService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BTNController @Inject() (
  cc:         ControllerComponents,
  authFilter: AuthActionFilter,
  btnService: BTNService
)(using ec:   ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitBTN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        request.body
          .validate[BTNRequest]
          .fold(
            _ => Future.failed(HIPBadRequest()),
            btnRequest => handleSubmission(pillar2Id, btnRequest)
          )
      }
  }

  def handleSubmission(pillar2Id: String, request: BTNRequest): Future[Result] =
    pillar2Id match {
      case ServerErrorPlrId => Future.failed(ETMPInternalServerError)
      case _ =>
        btnService
          .submitBTN(pillar2Id, request)
          .map(_ => Created(Json.toJson(BTN_SUCCESS_201)))
    }
}
