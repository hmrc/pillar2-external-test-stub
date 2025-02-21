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

import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{reset, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRLiabilityReturn, UKTRNilReturn, UKTRSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository

import java.time._
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

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[UKTRSubmissionRepository].toInstance(mockRepository))
      .build()

  override def beforeEach(): Unit = reset(mockRepository)

  "return OK with success response for a valid uktr amendment" in {
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "chargeReference").as[String] mustEqual "XTC01234123412"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY when X-Pillar2-Id header is missing" in {
    val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader)
      .withBody(Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "002"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "PLR Reference is missing or invalid"
  }

  "return UNPROCESSABLE_ENTITY when subscription is not found for the given PLR reference" in {
    val nonExistentPlrId = "XEPLR5555555554"
    val request          = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "007"
    (jsonResult \ "errors" \ "text").as[String] mustEqual s"No active subscription found for PLR Reference: $nonExistentPlrId"
  }

  "return UNPROCESSABLE_ENTITY when amendment to a liability return that does not exist" in {
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]), any[String]))
      .thenReturn(Future.successful(Left(DetailedErrorResponse(RequestCouldNotBeProcessed))))

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
  }

  "return OK with success response for a valid NIL_RETURN amendment" in {
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY when amendment to a nil return that does not exist" in {
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]), any[String]))
      .thenReturn(Future.successful(Left(DetailedErrorResponse(RequestCouldNotBeProcessed))))

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
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
    val request     = createRequest(validPlrId, invalidJson)

    val result = route(app, request).value
    status(result) mustBe BAD_REQUEST
  }

  "return BAD_REQUEST for non-JSON data" in {
    val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader)
      .withHeaders("X-Pillar2-Id" -> validPlrId)
      .withBody("non-json body")

    val result = route(app, request).value
    status(result) mustBe BAD_REQUEST
  }

  "return UNPROCESSABLE_ENTITY if liableEntities array is empty" in {
    val emptyLiabilityData = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr())))

    val request = createRequest(validPlrId, Json.toJson(emptyLiabilityData))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "liabilityEntity cannot be empty"
  }
}
