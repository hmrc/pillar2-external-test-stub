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

import cats.data.NonEmptyChain
import cats.implicits._
import play.api.Logging
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.repsonse.ErrorResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.repsonse.SubmitUKTRSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationError
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.ValidationResult
import uk.gov.hmrc.pillar2externalteststub.validation.syntax._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future} // imports helper method

@Singleton
class SubmitUKTRController @Inject() (
  cc:          ControllerComponents,
  authFilter:  AuthActionFilter
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
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
        validateRequest(request)
    }
  }

  def validateRequest(request: Request[JsValue]): Future[Result] =
    request.body.validate[UktrSubmission] match {
      case JsSuccess(uktrRequest: UktrSubmissionData, _) =>
        validateUktrSubmissionData(uktrRequest: UktrSubmissionData).flatMap {
          case Left(errors) =>
            Future.successful(BadRequest(UktrSubmissionErrorJsonConverter.toJson(errors)))
          case Right(_) =>
            Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
        }
      case JsSuccess(nilReturnRequest: UktrSubmissionNilReturn, _) =>
        validateNilReturn(nilReturnRequest: UktrSubmissionNilReturn).flatMap {
          case Left(errors) => Future.successful(BadRequest(Json.toJson(errors.toList.map(error => s"${error.field}: ${error.errorMessage}"))))
          case Right(_) =>
            Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
        }
      case JsError(errors) =>
        Future.successful {
          logger.info("JsErrors. Not NilReturn or UKTRSubmission. Did not validate. errors=" + errors)
          BadRequest(Json.toJson(errors.map(_._1.toJsonString)))
        }
      case _ =>
        Future.successful {
          BadRequest("Unknown Request type: must be either UktrSubmissionData or NilReturn.")
        }
    }

  def validateUktrSubmissionData(req: UktrSubmissionData): Future[Either[NonEmptyChain[ValidationError], UktrSubmissionData]] = {
    val validationResult: ValidationResult[UktrSubmissionData] = req.validate
    Future.successful(validationResult.toEither)
  }

  def validateNilReturn(nilReturnRequest: UktrSubmissionNilReturn): Future[Either[NonEmptyChain[ValidationError], UktrSubmissionNilReturn]] = {
    // Placeholder for NilReturn validation
    Future.successful(Right(nilReturnRequest))
  }
}
