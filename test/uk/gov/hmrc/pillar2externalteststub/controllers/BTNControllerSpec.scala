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
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json._
import play.api.mvc.Headers
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.models.btn._

import java.time.LocalDate
import scala.util.Random

class BTNControllerSpec extends AnyFunSuite with Matchers with GuiceOneAppPerSuite with OptionValues {

  val validHeaders: List[(String, String)] = List(HeaderNames.authorisation).map(_ -> Random.nextString(10))

  def request(implicit pillar2Id: String): FakeRequest[JsValue] =
    FakeRequest(POST, routes.BTNController.submitBTN.url)
      .withHeaders(Headers(validHeaders: _*))
      .withHeaders("X-PILLAR2-ID" -> pillar2Id)
      .withBody(Json.toJson(BTNRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1))))

  test("Valid BTN submission") {
    implicit val pillar2Id: String = "XMPLR00000000000"
    val result = route(app, request).value

    status(result) shouldEqual CREATED
    contentAsJson(result).validate[BTNSuccessResponse].asEither.isRight shouldBe true
  }

  test("Missing X-Pillar2-Id header") {
    val requestWithoutId = FakeRequest(POST, routes.BTNController.submitBTN.url)
      .withHeaders(Headers(validHeaders: _*))
      .withBody(Json.toJson(BTNRequest(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 1, 1))))

    val result = route(app, requestWithoutId).value

    status(result) shouldEqual UNPROCESSABLE_ENTITY
    contentAsJson(result).validate[BTNFailureResponse].asEither.isRight shouldBe true
    contentAsJson(result).as[BTNFailureResponse].errors.code shouldEqual "002"
  }

  test("Invalid accounting period") {
    implicit val pillar2Id: String = "XMPLR00000000000"
    val invalidPeriodRequest = FakeRequest(POST, routes.BTNController.submitBTN.url)
      .withHeaders(Headers(validHeaders: _*))
      .withHeaders("X-PILLAR2-ID" -> pillar2Id)
      .withBody(Json.toJson(BTNRequest(LocalDate.of(2025, 1, 1), LocalDate.of(2024, 1, 1))))

    val result = route(app, invalidPeriodRequest).value

    status(result) shouldEqual UNPROCESSABLE_ENTITY
    contentAsJson(result).validate[BTNFailureResponse].asEither.isRight shouldBe true
    contentAsJson(result).as[BTNFailureResponse].errors.code shouldEqual "003"
  }

  test("Invalid JSON body") {
    implicit val pillar2Id: String = "XMPLR00000000000"
    val invalidRequest = FakeRequest(POST, routes.BTNController.submitBTN.url)
      .withHeaders(Headers(validHeaders: _*))
      .withHeaders("X-PILLAR2-ID" -> pillar2Id)
      .withTextBody("invalid json")

    val result = route(app, invalidRequest).value

    status(result) shouldEqual BAD_REQUEST
    contentAsJson(result).validate[BTNErrorResponse].asEither.isRight shouldBe true
    contentAsJson(result).as[BTNErrorResponse].error.code shouldEqual "400"
  }

  test("Bad Request BTN submission") {
    implicit val pillar2Id: String = "XEPLR0000000400"
    val result = route(app, request).value

    status(result) shouldEqual BAD_REQUEST
    contentAsJson(result).validate[BTNErrorResponse].asEither.isRight shouldBe true
    contentAsJson(result).as[BTNErrorResponse].error.code shouldEqual "400"
  }

  test("Unprocessable entity BTN submission") {
    implicit val pillar2Id: String = "XEPLR0000000422"
    val result = route(app, request).value

    status(result) shouldEqual UNPROCESSABLE_ENTITY
    contentAsJson(result).validate[BTNFailureResponse].asEither.isRight shouldBe true
    contentAsJson(result).as[BTNFailureResponse].errors.code shouldEqual "422"
  }

  test("Internal Server Error BTN submission") {
    implicit val pillar2Id: String = "XEPLR0000000500"
    val result = route(app, request).value

    status(result) shouldEqual INTERNAL_SERVER_ERROR
    contentAsJson(result).validate[BTNErrorResponse].asEither.isRight shouldBe true
    contentAsJson(result).as[BTNErrorResponse].error.code shouldEqual "500"
  }
}
