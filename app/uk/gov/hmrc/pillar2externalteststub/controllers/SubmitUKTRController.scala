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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UktrSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.SAPError500
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.ValidationError422
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.{DetailedError, SimpleError}
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

    request.body
      .validate[UktrSubmission]
      .fold(
        errors => Future.successful(BadRequest(Json.toJson(ErrorResponse(DetailedError(ValidationError422.response))))),
        subscriptionRequest => processSubmissionRequest(plrReference, subscriptionRequest)
      )
  }

  private def processSubmissionRequest(pillar2ID: String, subscriptionRequest: UktrSubmission): Future[Result] =
    pillar2ID match {
      case "P2ID0000000422" =>
        Future.successful(UnprocessableEntity(Json.toJson(ErrorResponse(DetailedError(ValidationError422.response)))))
      case "P2ID0000000500" =>
        Future.successful(InternalServerError(Json.toJson(ErrorResponse(SimpleError(SAPError500.response)))))
      case _ =>
        logger.info(s"...Submitting UKTR subscription SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse()...")
        Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
    }
}
