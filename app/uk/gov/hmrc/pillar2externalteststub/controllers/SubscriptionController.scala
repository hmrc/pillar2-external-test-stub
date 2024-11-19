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
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.Future

class SubscriptionController @Inject() (
  cc:         ControllerComponents,
  authFilter: AuthActionFilter
) extends BackendController(cc)
    with Logging {

  def retrieveSubscription(plrReference: String): Action[AnyContent] =
    (Action andThen authFilter).async { implicit request =>
      logger.info(s"Retrieving subscription for PLR reference: $plrReference")

      plrReference match {
        case "XEPLR0123456404" =>
          Future.successful(NotFound(Json.toJson(NotFoundSubscription.response)))
        case "XEPLR0123456500" =>
          Future.successful(InternalServerError(Json.toJson(ServerError500.response)))
        case "XEPLR0123456503" =>
          Future.successful(ServiceUnavailable(Json.toJson(ServiceUnavailable503.response)))
        case "XEPLR5555555555" =>
          Future.successful(Ok(Json.toJson(SubscriptionSuccessResponse.successfulDomesticOnlyResponse(plrReference))))
        case "XEPLR1234567890" =>
          Future.successful(Ok(Json.toJson(SubscriptionSuccessResponse.successfulNonDomesticResponse(plrReference))))
        case "XEPLR0987654321" =>
          Future.successful(Ok(Json.toJson(NilReturnSuccess.successfulResponse)))
        case _ =>
          Future.successful(NotFound(Json.toJson(NotFoundSubscription.response)))
      }
    }
}
