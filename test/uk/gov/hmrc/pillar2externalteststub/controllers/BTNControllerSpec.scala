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
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest
import uk.gov.hmrc.pillar2externalteststub.services.BTNService

import scala.concurrent.Future

class BTNControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with BTNDataFixture with TestOrgDataFixture {

  private val mockBTNService = mock[BTNService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[BTNService].toInstance(mockBTNService))
      .build()

  "Below Threshold Notification" - {
    "when submitting a notification" - {
      "should return CREATED with success response for a valid submission" in {
        when(mockBTNService.submitBTN(eqTo(validPlrId), any[BTNRequest])).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined shouldBe true
      }

      "should return IdMissingOrInvalid when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/RESTAdapter/plr/below-threshold-notification")
          .withHeaders(hipHeaders: _*)
          .withBody(validBTNRequestBody)

        val result = route(app, request).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return IdMissingOrInvalid when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createRequestWithBody(invalidPlrId, validBTNRequest)).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return ETMPBadRequest when request body is invalid JSON" in {
        val result = route(app, createRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith HIPBadRequest()
      }

      "should return ETMPInternalServerError when specific Pillar2 ID indicates server error" in {
        val result = route(app, createRequestWithBody(serverErrorPlrId, validBTNRequest)).get
        result shouldFailWith ETMPInternalServerError
      }
    }
  }
}
