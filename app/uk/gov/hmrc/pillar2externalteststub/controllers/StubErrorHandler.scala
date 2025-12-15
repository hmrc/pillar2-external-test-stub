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
import uk.gov.hmrc.pillar2externalteststub.models.error.*
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.*
import uk.gov.hmrc.pillar2externalteststub.models.response.*
import uk.gov.hmrc.pillar2externalteststub.models.response.Origin.HIP

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
          case e @ TestDataNotFound(_)          => Results.NotFound(Json.toJson(StubErrorResponse(e.code, e.message)))
        }
        logger.warn(s"Caught StubError. Returning ${ret.header.status} statuscode", exception)
        Future.successful(ret)
      case e: ETMPError =>
        val ret = e match {
          case IdMissingOrInvalid | RequestCouldNotBeProcessed | NoFormBundleFound | NoActiveSubscription | NoDataFound |
              TaxObligationAlreadyFulfilled | InvalidReturn | InvalidDTTElection | InvalidUTPRElection | InvalidTotalLiability |
              InvalidTotalLiabilityIIR | InvalidTotalLiabilityDTT | InvalidTotalLiabilityUTPR | AccountingPeriodUnderEnquiry =>
            Results.UnprocessableEntity(Json.toJson(ETMPFailureResponse(ETMPDetailedError(e.code, e.message))))
          case ETMPInternalServerError => Results.InternalServerError(Json.toJson(ETMPErrorResponse(ETMPSimpleError(e))))
        }
        logger.warn(s"Caught ETMPError. Returning ${ret.header.status} statuscode", exception)
        Future.successful(ret)
      case HIPBadRequest(_) =>
        Future.successful(Results.BadRequest(Json.toJson(HIPErrorResponse(HIP, HIPFailure(List(HIPError("json error", "json error")))))))
      case _ =>
        logger.error("Unhandled exception. Returning 500 statuscode", exception)
        Future.successful(Results.InternalServerError(Json.toJson(ETMPErrorResponse(ETMPSimpleError(ETMPInternalServerError)))))
    }
}
