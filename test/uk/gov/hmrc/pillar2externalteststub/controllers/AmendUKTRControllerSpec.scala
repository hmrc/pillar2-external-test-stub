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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper._
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn}
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository

import java.time.ZonedDateTime
import scala.concurrent.Future

class AmendUKTRControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with UKTRDataFixture
    with MockitoSugar
    with BeforeAndAfterEach {

  private val mockRepository = mock[UKTRSubmissionRepository]

  private val invalidRequestBody = Json.obj("invalidField" -> "value")

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[UKTRSubmissionRepository].toInstance(mockRepository))
      .build()

  override def beforeEach(): Unit = reset(mockRepository)

  "AmendUKTRController" - {
    "successful scenarios" - {
      "should return OK with success response for a valid liability return" in {
        when(mockRepository.update(any[UKTRLiabilityReturn], any[String]))
          .thenReturn(Future.successful(Right(true)))

        val request = createRequest(PlrId, Json.toJson(validRequestBody))

        val result = route(app, request).value
        status(result) shouldBe OK
        val jsonResult = contentAsJson(result)
        (jsonResult \ "success" \ "formBundleNumber").as[String] shouldEqual "119000004320"
        (jsonResult \ "success" \ "chargeReference").as[String] shouldEqual "XTC01234123412"
        (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined shouldBe true
      }

      "should return OK with success response for a valid nil return" in {
        when(mockRepository.update(any[UKTRNilReturn], any[String]))
          .thenReturn(Future.successful(Right(true)))

        val request = createRequest(PlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

        val result = route(app, request).value
        status(result) shouldBe OK
        val jsonResult = contentAsJson(result)
        (jsonResult \ "success" \ "formBundleNumber").as[String] shouldEqual "119000004320"
        (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined shouldBe true
      }
    }

    "validation errors" - {
      "should return UNPROCESSABLE_ENTITY when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(Json.toJson(validRequestBody))

        val result = route(app, request).value
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] shouldEqual "002"
        (jsonResult \ "errors" \ "text").as[String] shouldEqual "Pillar 2 ID missing or invalid"
      }

      "should return UNPROCESSABLE_ENTITY when subscription is not found" in {
        val nonExistentPlrId = "XEPLR5555555554"
        val request          = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))

        val result = route(app, request).value
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] shouldEqual "007"
        (jsonResult \ "errors" \ "text").as[String] shouldEqual "Unable to fetch subscription for pillar2 ID: XEPLR5555555554"
      }

      "should return BAD_REQUEST for invalid JSON structure" in {
        val request = createRequest(PlrId, invalidRequestBody)
        val result  = route(app, request).value
        status(result) shouldBe BAD_REQUEST
      }

      "should return BAD_REQUEST for non-JSON data" in {
        val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
          .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> PlrId)
          .withBody("non-json body")

        val result = route(app, request).value
        status(result) shouldBe BAD_REQUEST
      }

      "should return UNPROCESSABLE_ENTITY if liableEntities array is empty" in {
        val emptyLiabilityData = validRequestBody
          .as[JsObject]
          .deepMerge(
            Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr()))
          )

        val request = createRequest(PlrId, Json.toJson(emptyLiabilityData))

        val result = route(app, request).value
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] shouldEqual "093"
        (jsonResult \ "errors" \ "text").as[String] shouldEqual "liabilityEntity cannot be empty"
      }
    }

    "database errors" - {
      "should return UNPROCESSABLE_ENTITY for liability return database errors" in {
        when(mockRepository.update(any[UKTRLiabilityReturn], any[String]))
          .thenReturn(Future.failed(DatabaseError(RequestCouldNotBeProcessed.errors.text)))

        val request = createRequest(PlrId, Json.toJson(validRequestBody))

        val result = route(app, request).value
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] shouldEqual "003"
        (jsonResult \ "errors" \ "text").as[String] shouldEqual "Request could not be processed"
      }

      "should return UNPROCESSABLE_ENTITY for nil return database errors" in {
        when(mockRepository.update(any[UKTRNilReturn], any[String]))
          .thenReturn(Future.failed(DatabaseError(RequestCouldNotBeProcessed.errors.text)))

        val request = createRequest(PlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

        val result = route(app, request).value
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] shouldEqual "003"
        (jsonResult \ "errors" \ "text").as[String] shouldEqual "Request could not be processed"
      }
    }

    "edge cases" - {
      "should return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
        val request = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

        val result = route(app, request).value
        status(result) shouldBe INTERNAL_SERVER_ERROR
        val jsonResult = contentAsJson(result)
        (jsonResult \ "error" \ "code").as[String] shouldEqual "500"
        (jsonResult \ "error" \ "message").as[String] shouldEqual "Internal server error"
      }

      "should handle missing Content-Type header" in {
        val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
          .withHeaders(authHeader, "X-Pillar2-Id" -> PlrId)
          .withBody("test body")

        val result = route(app, request).value
        status(result) shouldBe UNSUPPORTED_MEDIA_TYPE
      }

      "should handle invalid Content-Type header" in {
        val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
          .withHeaders(authHeader, "X-Pillar2-Id" -> PlrId)
          .withHeaders("Content-Type" -> "text/plain")
          .withBody("test body")

        val result = route(app, request).value
        status(result) shouldBe UNSUPPORTED_MEDIA_TYPE
      }
    }
  }
}
