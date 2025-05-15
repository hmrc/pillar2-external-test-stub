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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.{TestOrgDataFixture, UKTRDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.LiabilityReturnSuccess.successfulUKTRResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.services.UKTRService

import scala.concurrent.Future

class UKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with UKTRDataFixture with TestOrgDataFixture {

  private val mockUKTRService = mock[UKTRService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[UKTRService].toInstance(mockUKTRService))
      .build()

  "UK Tax Return" - {
    "when submitting a return" - {
      "should return CREATED with success response for a valid liability return submission" in {
        when(mockUKTRService.submitUKTR(eqTo(validPlrId), any[UKTRSubmission]))
          .thenReturn(Future.successful(successfulUKTRResponse()))

        val result = route(app, createRequestWithBody(validPlrId, liabilitySubmission)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
        (json \ "success" \ "chargeReference").asOpt[String].isDefined  shouldBe true
      }

      "should return CREATED with success response for a valid nil return submission" in {
        when(mockUKTRService.submitUKTR(eqTo(validPlrId), any[UKTRSubmission]))
          .thenReturn(Future.successful(successfulNilReturnResponse))

        val result = route(app, createRequestWithBody(validPlrId, nilSubmission)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
      }

      "should return IdMissingOrInvalid when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/RESTAdapter/plr/uk-tax-return")
          .withHeaders(hipHeaders: _*)
          .withBody(validRequestBody)

        val result = route(app, request).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return IdMissingOrInvalid when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createRequestWithBody(invalidPlrId, liabilitySubmission)).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return ETMPBadRequest when request body is invalid JSON" in {
        val result = route(app, createRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest()
      }

      "should return ETMPInternalServerError when specific Pillar2 ID indicates server error" in {
        val result = route(app, createRequestWithBody(serverErrorPlrId, liabilitySubmission)).get
        result shouldFailWith ETMPInternalServerError
      }
    }

    "when amending a return" - {
      "should return OK with success response for a valid liability return amendment" in {
        when(mockUKTRService.amendUKTR(eqTo(validPlrId), any[UKTRSubmission]))
          .thenReturn(Future.successful(successfulUKTRResponse(Some("EXISTING-REF"))))

        val result = route(app, createRequestWithBody(validPlrId, liabilitySubmission, isAmend = true)).get
        status(result) shouldBe OK
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
        (json \ "success" \ "chargeReference").as[String]               shouldBe "EXISTING-REF"
      }

      "should return OK with success response for a valid nil return amendment" in {
        when(mockUKTRService.amendUKTR(eqTo(validPlrId), any[UKTRSubmission]))
          .thenReturn(Future.successful(successfulNilReturnResponse))

        val result = route(app, createRequestWithBody(validPlrId, nilSubmission, isAmend = true)).get
        status(result) shouldBe OK
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
      }

      "should return ETMPBadRequest when amendment request body is invalid JSON" in {
        val result = route(app, createAmendRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest()
      }
    }
  }

  private def createRequestWithBody(pillar2Id: String, body: UKTRSubmission, isAmend: Boolean = false) = {
    val method = if (isAmend) PUT else POST
    val url    = if (isAmend) "/RESTAdapter/plr/uk-tax-return" else "/RESTAdapter/plr/uk-tax-return"
    FakeRequest(method, url)
      .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*)
      .withBody(Json.toJson(body))
  }

  private def createRequest(pillar2Id: String, body: JsValue) =
    FakeRequest(POST, "/RESTAdapter/plr/uk-tax-return")
      .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*)
      .withBody(body)

  private def createAmendRequest(pillar2Id: String, body: JsValue) =
    FakeRequest(PUT, "/RESTAdapter/plr/uk-tax-return")
      .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*)
      .withBody(body)
}
