/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.{GIRDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.services.GIRService

import scala.concurrent.Future

class GIRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with GIRDataFixture with TestOrgDataFixture {

  private val mockGIRService = mock[GIRService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[GIRService].toInstance(mockGIRService))
      .build()

  "GIR Controller" - {
    "when submitting a GIR" - {
      "should return CREATED with success response for a valid submission" in {
        when(mockGIRService.submitGIR(eqTo(validPlrId), any[GIRRequest])).thenReturn(Future.successful(true))

        val result = route(app, createGIRRequestWithBody(validPlrId, validGIRRequest)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined shouldBe true
      }

      "should return IdMissingOrInvalid when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/pillar2/test/globe-information-return")
          .withHeaders(hipHeaders: _*)
          .withBody(validGIRRequestBody)

        val result = route(app, request).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return IdMissingOrInvalid when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createGIRRequestWithBody(invalidPlrId, validGIRRequest)).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return ETMPBadRequest when request body is invalid JSON" in {
        val result = route(app, createGIRRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith HIPBadRequest()
      }

      "should return ETMPInternalServerError when specific Pillar2 ID indicates server error" in {
        val result = route(app, createGIRRequestWithBody(serverErrorPlrId, validGIRRequest)).get
        result shouldFailWith ETMPInternalServerError
      }
    }
  }
}
