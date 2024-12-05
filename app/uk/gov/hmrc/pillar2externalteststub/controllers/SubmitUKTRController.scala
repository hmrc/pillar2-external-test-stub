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
    plrReference match {
      case "XEPLR0000000422" =>
        Future.successful(UnprocessableEntity(Json.toJson(ErrorResponse.detailed(ValidationError422RegimeMissingOrInvalid.response))))
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
            Future.successful(UnprocessableEntity(UktrSubmissionErrorJsonConverter.toJson(errors)))
          case Right(_) =>
            Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
        }
      case JsSuccess(nilReturnRequest: UktrSubmissionNilReturn, _) =>
        validateNilReturn(nilReturnRequest: UktrSubmissionNilReturn).flatMap {
          case Left(errors) =>
            Future.successful(UnprocessableEntity(Json.toJson(errors.toList.map(error => s"${error.field}: ${error.errorMessage}"))))
          case Right(_) =>
            Future.successful(Created(Json.toJson(SubmitUKTRSuccessResponse.successfulDomesticOnlyResponse())))
        }
      case JsError(errors) =>
        val errorsToString = errors.toString()
        Future.successful {
          formatMissingMandatoryFieldErrorMessage(errorsToString)
        }
      case _ =>
        Future.successful {
          BadRequest("Unknown Request type: Submit UKTR Request Body must be either UktrSubmissionData or NilReturn.")
        }
    }

  def formatMissingMandatoryFieldErrorMessage(jsonErrorListString: String): Result = {
    val PATH_MISSING_STRING = "JsonValidationError(List(error.path.missing)"
    jsonErrorListString match {
      case str if str.contains("ukChargeableEntityName") && str.contains(PATH_MISSING_STRING) =>
        BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400MissingUkChargeableEntityName.response)))
      case str if str.contains("idType") && str.contains(PATH_MISSING_STRING) =>
        BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400MissingIdType.response)))
      case str if str.contains("idValue") && str.contains(PATH_MISSING_STRING) =>
        BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400MissingIdValue.response)))
      case str if str.contains("amountOwedDTT") && str.contains(PATH_MISSING_STRING) =>
        BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400MissingAmountOwedDTT.response)))
      case str if str.contains("amountOwedIIR") && str.contains(PATH_MISSING_STRING) =>
        BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400MissingAmountOwedIIR.response)))
      case str if str.contains("amountOwedUTPR") && str.contains(PATH_MISSING_STRING) =>
        BadRequest(Json.toJson(ErrorResponse.simple(InvalidJsonError400MissingAmountOwedUTPR.response)))
      case _ =>
        BadRequest(Json.toJson(jsonErrorListString))
    }
  }

  def validateUktrSubmissionData(req: UktrSubmissionData): Future[Either[NonEmptyChain[ValidationError], UktrSubmissionData]] = {
    val validationResult: ValidationResult[UktrSubmissionData] = req.validate
    Future.successful(validationResult.toEither)
  }

  def validateNilReturn(nilReturnRequest: UktrSubmissionNilReturn): Future[Either[NonEmptyChain[ValidationError], UktrSubmissionNilReturn]] =
    // Placeholder for NilReturn validation
    Future.successful(Right(nilReturnRequest))
}
