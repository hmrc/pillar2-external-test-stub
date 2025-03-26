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
import play.api.libs.json.Json
import play.api.mvc.Results.Status
import play.api.mvc.{RequestHeader, Result, Results}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error._
import uk.gov.hmrc.pillar2externalteststub.models.response._

import javax.inject.Singleton
import scala.concurrent.Future

@Singleton
class StubErrorHandler extends HttpErrorHandler with Logging {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] =
    Future.successful(Status(statusCode))

  override def onServerError(request: RequestHeader, exception: Throwable): Future[Result] =
    exception match {
      case e: StubError =>
        val ret = e match {
          case e @ InvalidJson                  => Results.BadRequest(Json.toJson(StubErrorResponse(e.code, e.message)))
          case e @ EmptyRequestBody             => Results.BadRequest(Json.toJson(StubErrorResponse(e.code, e.message)))
          case e @ OrganisationAlreadyExists(_) => Results.Conflict(Json.toJson(StubErrorResponse(e.code, e.message)))
          case e @ OrganisationNotFound(_)      => Results.NotFound(Json.toJson(StubErrorResponse(e.code, e.message)))
          case e @ DatabaseError(_)             => Results.InternalServerError(Json.toJson(StubErrorResponse(e.code, e.message)))
        }
        logger.warn(s"Caught StubError. Returning ${ret.header.status} statuscode", exception)
        Future.successful(ret)
      case e: ETMPError =>
        val ret = e match {
          case Pillar2IdMissing | RequestCouldNotBeProcessed | DuplicateSubmission | NoActiveSubscription | NoAssociatedDataFound |
              TaxObligationAlreadyFulfilled | InvalidReturn | InvalidDTTElection | InvalidUTPRElection | InvalidTotalLiability |
              InvalidTotalLiabilityIIR | InvalidTotalLiabilityDTT | InvalidTotalLiabilityUTPR =>
            Results.UnprocessableEntity(Json.toJson(ETMPFailureResponse(ETMPDetailedError(e.code, e.message))))
          case ETMPBadRequest          => Results.BadRequest(Json.toJson(ETMPErrorResponse(ETMPSimpleError(e))))
          case ETMPInternalServerError => Results.InternalServerError(Json.toJson(ETMPErrorResponse(ETMPSimpleError(e))))
        }
        logger.warn(s"Caught ETMPError. Returning ${ret.header.status} statuscode", exception)
        Future.successful(ret)
      case _ =>
        logger.error("Unhandled exception. Returning 500 statuscode", exception)
        Future.successful(Results.InternalServerError(Json.toJson(ETMPErrorResponse(ETMPSimpleError(ETMPInternalServerError)))))
    }
}
