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
import play.api.http.HttpErrorHandler
import play.api.http.Status._
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Result, Results}
import uk.gov.hmrc.pillar2externalteststub.models.error._
import uk.gov.hmrc.pillar2externalteststub.models.response.StubErrorResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes

import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class StubErrorHandler extends HttpErrorHandler with Logging {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    statusCode match {
      case BAD_REQUEST =>
        Future.successful(
          Results.BadRequest(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "400",
                "message" -> "Bad request",
                "errors"  -> message
              )
            )
          )
        )
      case UNAUTHORIZED =>
        Future.successful(
          Results.Unauthorized(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "401",
                "message" -> "Unauthorized"
              )
            )
          )
        )
      case FORBIDDEN =>
        Future.successful(
          Results.Forbidden(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "403",
                "message" -> "Forbidden"
              )
            )
          )
        )
      case NOT_FOUND =>
        Future.successful(
          Results.NotFound(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "404",
                "message" -> "Not found"
              )
            )
          )
        )
      case UNPROCESSABLE_ENTITY =>
        Future.successful(
          Results.UnprocessableEntity(
            Json.obj(
              "errors" -> Json.obj(
                "code" -> UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                "text" -> message
              )
            )
          )
        )
      case _ =>
        Future.successful(
          Results.Status(statusCode)(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> statusCode.toString,
                "message" -> message
              )
            )
          )
        )
    }

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    exception match {
      case e: StubError =>
        val ret = e match {
          case InvalidJson                  => Results.BadRequest(Json.toJson(StubErrorResponse(e.code, e.message)))
          case EmptyRequestBody             => Results.BadRequest(Json.toJson(StubErrorResponse(e.code, e.message)))
          case OrganisationAlreadyExists(_) => Results.Conflict(Json.toJson(StubErrorResponse(e.code, e.message)))
          case OrganisationNotFound(_)      => Results.NotFound(Json.toJson(StubErrorResponse(e.code, e.message)))
          case DatabaseError(_)             => Results.InternalServerError(Json.toJson(StubErrorResponse(e.code, e.message)))
          case InvalidAccountingPeriod(_, _, _, _) =>
            Results.UnprocessableEntity(
              Json.obj(
                "errors" -> Json.obj(
                  "code" -> UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                  "text" -> e.message
                )
              )
            )
          case InvalidPillar2Id(_) =>
            Results.UnprocessableEntity(
              Json.obj(
                "errors" -> Json.obj(
                  "code" -> UKTRErrorCodes.PILLAR_2_ID_MISSING_OR_INVALID_002,
                  "text" -> "PLR Reference is missing or invalid"
                )
              )
            )
          case _: DuplicateSubmissionError =>
            Results.UnprocessableEntity(
              Json.obj(
                "errors" -> Json.obj(
                  "code" -> UKTRErrorCodes.DUPLICATE_SUBMISSION_044,
                  "text" -> "A submission already exists for this accounting period"
                )
              )
            )
          case submissionError: SubmissionNotFoundError =>
            Results.UnprocessableEntity(
              Json.obj(
                "errors" -> Json.obj(
                  "code" -> UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                  "text" -> submissionError.message
                )
              )
            )
          case _: DomesticOnlyMTTError =>
            Results.UnprocessableEntity(
              Json.obj(
                "errors" -> Json.obj(
                  "code" -> UKTRErrorCodes.INVALID_RETURN_093,
                  "text" -> "obligationMTT cannot be true for a domestic-only group"
                )
              )
            )
        }
        logger.warn(s"Caught StubError. Returning ${ret.header.status} statuscode", exception)
        Future.successful(ret)
      case _ =>
        logger.error("Unhandled exception. Returning 500 statuscode", exception)
        Future.successful(
          Results.InternalServerError(
            Json.obj(
              "error" -> Json.obj(
                "code"    -> "500",
                "message" -> "Internal server error",
                "logID"   -> "C0000000000000000000000000000500"
              )
            )
          )
        )
    }
}
