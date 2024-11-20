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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.SubmitUKTRSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UktrSubmission
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

    println(s"request.body: ${request.body}")

    request.body
      .validate[UktrSubmission]
      .fold(
        errors => Future.successful(handle400Error),
        subscriptionRequest => processSubmissionRequest(plrReference, subscriptionRequest)
      )
  }
  private def processSubmissionRequest(pillar2ID: String, subscriptionRequest: UktrSubmission): Future[Result] = {
    println("processSubmissionRequest...")
    pillar2ID match {
      case "P2ID0000000422" =>
        // Scenario 2: Business validation failure - HTTP 422
        Future.successful(UnprocessableEntity(Json.toJson(ValidationError422.response)))

      case "P2ID0000000500" =>
        // Scenario 3: Back-end SAP failure - HTTP 500
        Future.successful(InternalServerError(Json.toJson(SAPError500.response)))

      case _ =>
        // Default success response or other cases
        logger.info(s"...Submitting UKTR subscription SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse()...")
        Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
    }
  }

  private def handle400Error: Result =
    // Scenario 4: Malformed JSON request payload - HTTP 400
    BadRequest(Json.toJson(InvalidJsonError400.response))
}
