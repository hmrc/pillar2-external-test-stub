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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.models._

import scala.concurrent.Future

class SubscriptionControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues {

  private def authorizedRequest(plrReference: String) =
    FakeRequest(GET, routes.SubscriptionController.retrieveSubscription(plrReference).url)
      .withHeaders(HeaderNames.authorisation -> "Bearer valid_token")

  private def unauthorizedRequest(plrReference: String) =
    FakeRequest(GET, routes.SubscriptionController.retrieveSubscription(plrReference).url)

  "SubscriptionController" - {

    "retrieveSubscription" - {

      "must return FORBIDDEN response when 'Authorization' header is missing" in {
        val result: Future[Result] = route(app, unauthorizedRequest("XEPLR0123456400")).value
        status(result) shouldBe FORBIDDEN
      }

      "must return BAD_REQUEST for plrReference 'XEPLR0123456400' with INVALID_CORRELATIONID" in {
        val result = route(app, authorizedRequest("XEPLR0123456400")).value
        status(result)        shouldBe BAD_REQUEST
        contentAsJson(result) shouldBe Json.toJson(BadRequestInvalidCorrelationID.response)
      }

      "must return NOT_FOUND for plrReference 'XEPLR0123456404' with SUBSCRIPTION_NOT_FOUND" in {
        val result = route(app, authorizedRequest("XEPLR0123456404")).value
        status(result)        shouldBe NOT_FOUND
        contentAsJson(result) shouldBe Json.toJson(NotFoundSubscription.response)
      }

      "must return UNPROCESSABLE_ENTITY for plrReference 'XEPLR0123456422' with DUPLICATE_RECORD" in {
        val result = route(app, authorizedRequest("XEPLR0123456422")).value
        status(result)        shouldBe UNPROCESSABLE_ENTITY
        contentAsJson(result) shouldBe Json.toJson(DuplicateRecord422.response)
      }

      "must return INTERNAL_SERVER_ERROR for plrReference 'XEPLR0123456500' with SERVER_ERROR" in {
        val result = route(app, authorizedRequest("XEPLR0123456500")).value
        status(result)        shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe Json.toJson(ServerError500.response)
      }

      "must return SERVICE_UNAVAILABLE for plrReference 'XEPLR0123456503' with SERVICE_UNAVAILABLE" in {
        val result = route(app, authorizedRequest("XEPLR0123456503")).value
        status(result)        shouldBe SERVICE_UNAVAILABLE
        contentAsJson(result) shouldBe Json.toJson(ServiceUnavailable503.response)
      }

      "must return OK with domesticOnly set to true for plrReference 'XEPLR5555555555'" in {
        val result = route(app, authorizedRequest("XEPLR5555555555")).value
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(SuccessResponse("XEPLR5555555555", domesticOnly = true))
      }

      "must return OK with domesticOnly set to false for any other plrReference" in {
        val result = route(app, authorizedRequest("XEPLR1234567890")).value
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(SuccessResponse("XEPLR1234567890", domesticOnly = false))
      }
    }
  }
}
