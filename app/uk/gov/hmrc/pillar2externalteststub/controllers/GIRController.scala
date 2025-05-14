/*
 * Copyright 2025 HM Revenue & Customs
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
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.services.GIRService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GIRController @Inject() (
  cc:          ControllerComponents,
  authFilter:  AuthActionFilter,
  girService:  GIRService
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitGIR: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id"))
      .flatMap { pillar2Id =>
        request.body
          .validate[GIRRequest]
          .fold(
            _ => Future.failed(ETMPBadRequest),
            girRequest =>
              girService
                .submitGIR(pillar2Id, girRequest)
                .map(_ => Created(Json.toJson(GIRSuccessResponse.GIR_SUCCESS_201)))
          )
      }
  }
}
