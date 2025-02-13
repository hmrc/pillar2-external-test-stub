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
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper._

import java.time._

class AmendUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture {

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  "return OK with success response for a valid uktr amendment" in {
    val request = createRequest(PlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "chargeReference").as[String] mustEqual "XTC01234123412"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return OK with success response for a valid NIL_RETURN amendment" in {
    val request = createRequest(PlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY with tax obligation already met error for specific Pillar2Id" in {
    val request = createRequest(TaxObligationMetPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "044"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Tax Obligation Already Fulfilled"
  }

  "return BAD_REQUEST for specific Pillar2Id" in {
    val request = createRequest(BadRequestPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe BAD_REQUEST
    val jsonResult = contentAsJson(result)
    (jsonResult \ "error" \ "code").as[String] mustEqual "400"
    (jsonResult \ "error" \ "message").as[String] mustEqual "Invalid JSON"
  }

  "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
    val request = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe INTERNAL_SERVER_ERROR
    val jsonResult = contentAsJson(result)
    (jsonResult \ "error" \ "code").as[String] mustEqual "500"
    (jsonResult \ "error" \ "message").as[String] mustEqual "Internal server error"
  }

  "return BAD_REQUEST for invalid JSON structure" in {
    val invalidJson = Json.obj("invalidField" -> "value")
    val request     = createRequest(PlrId, invalidJson)

    val result = route(app, request).value
    status(result) mustBe BAD_REQUEST
  }

  "return BAD_REQUEST for non-JSON data" in {
    val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader)
      .withHeaders("X-Pillar2-Id" -> PlrId)
      .withBody("non-json body")

    val result = route(app, request).value
    status(result) mustBe BAD_REQUEST
  }

  "return UNPROCESSABLE_ENTITY if liableEntities array is empty" in {
    val emptyLiabilityData = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr())))

    val request = createRequest(PlrId, Json.toJson(emptyLiabilityData))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "liabilityEntity cannot be empty"
  }
}
