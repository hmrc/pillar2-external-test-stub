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
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.Inject
import scala.concurrent.Future

class SubmitUKTRController @Inject() (
  cc:         ControllerComponents,
  authFilter: AuthActionFilter
) extends BackendController(cc)
    with Logging {

  def submitUKTR(plrReference: String): Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    logger.info(s"... Submitting UKTR subscription for PLR reference: $plrReference")

    request.body
      .validate[UKTRSubscriptionRequest]
      .fold(
        errors => Future.successful( BadRequest(Json.toJson(BadRequest400.response))),
        subscriptionRequest => processSubmissionRequest(plrReference, subscriptionRequest)
      )
  }
  private def processSubmissionRequest(pillar2ID: String, subscriptionRequest: UKTRSubscriptionRequest): Future[Result] = {
    println("processSubmissionRequest...")
    pillar2ID match {
      case "PILID0000000400" =>
        // Scenario 1: Bad Request / Invalid JSON - HTTP 400
        Future.successful(BadRequest(Json.toJson(BadRequest400.response)))
      case "PILID0000000401" =>
        // Scenario 2: Unauthorized Request - HTTP 401
        Future.successful(Unauthorized(Json.toJson(UnauthorizedRequest401.response)))
      case "PILID0000000403" =>
        // Scenario 3: Forbidden Request  - HTTP 403
        Future.successful(Forbidden(Json.toJson(ForbiddenRequest403.response)))
      case "PILID0000000404" =>
        // Scenario 4: URL Not Found - HTTP 404
        Future.successful(NotFound(Json.toJson(URLNotFound404.response)))
      case "PILID0000000415" =>
        // Scenario 5: Unsupported Media Type - HTTP 415
        Future.successful(UnsupportedMediaType(Json.toJson(UnsupportedMediaType415.response)))
      case "PILID0000000422" =>
        // Scenario 6: Business validation failure - HTTP 422. Unprocessable - ETMP validation errors.
        // ETMP successfully received the data without technical issues, however it is unable to process the data further.
        Future.successful(UnprocessableEntity(Json.toJson(UnprocessableETMPValidationErrors422.response)))
      case "PILID0000000500" =>
        // Scenario 7: Back-end server failure in ETMP - HTTP 500
        Future.successful(InternalServerError(Json.toJson(ServerError500.response)))
      case _ =>
        // Scenario 8 :Default success response for all other pillar2 IDs
        logger.info(s"...SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse()...")
        Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulResponse())))
    }
  }
}
