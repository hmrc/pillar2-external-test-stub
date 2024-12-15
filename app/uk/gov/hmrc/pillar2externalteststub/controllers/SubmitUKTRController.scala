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
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.response.SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.response.{ErrorResponse, SubmitUKTRSuccessResponse}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationError
import uk.gov.hmrc.pillar2externalteststub.validation.syntax.ValidateOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitUKTRController @Inject() (
  cc:          ControllerComponents,
  authFilter:  AuthActionFilter
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitUKTR(plrReference: String): Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    plrReference match {
      case "XEPLR0000000422" =>
        Future.successful(UnprocessableEntity(Json.toJson(ErrorResponse.detailed(ValidationError422RegimeMissingOrInvalid.response))))
      case "XEPLR0000000500" =>
        Future.successful(InternalServerError(Json.toJson(ErrorResponse.simple(SAPError500.response))))
      case "XEPLR0000000400" =>
        Future.successful(BadRequest(Json.toJson(ErrorResponse.simple(InvalidError400StaticErrorMessage.response))))
      case _ => validateRequest(plrReference, request)
    }
  }

  private def validateRequest(plrReference: String, request: Request[JsValue]): Future[Result] =
    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRSubmissionData, _) =>
        Future.successful(uktrRequest.validate(plrReference).toEither).flatMap {
          case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
          case Right(_)     => Future.successful(Created(Json.toJson(successfulDomesticOnlyResponse())))
        }
      case JsSuccess(nilReturnRequest: UKTRSubmissionNilReturn, _) =>
        validateNilReturn(nilReturnRequest: UKTRSubmissionNilReturn).flatMap {
          case Left(errors) =>
            Future.successful(UnprocessableEntity(Json.toJson(errors.toList.map(error => s"${error.field}: ${error.errorMessage}"))))
          case Right(_) =>
            Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
        }
      case JsError(errors) =>
        val concatenatedErrorMessages = errors
          .map { case (path, validationErrors) =>
            val fieldName     = path.toJsonString
            val errorMessages = validationErrors.map(_.message).mkString(", ")
            s"Field: $fieldName: $errorMessages"
          }
          .mkString("; ")
        Future.successful(BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400DynamicErrorMessage.response(concatenatedErrorMessages)))))
      case _ => Future.successful(BadRequest(Json.toJson(ErrorResponse.simple(InvalidError400StaticErrorMessage.response))))
    }

  def validateNilReturn(nilReturnRequest: UKTRSubmissionNilReturn): Future[Either[NonEmptyChain[ValidationError], UKTRSubmissionNilReturn]] =
    Future.successful(Right(nilReturnRequest))
}
