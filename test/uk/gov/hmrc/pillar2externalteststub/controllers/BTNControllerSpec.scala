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
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pillar2externalteststub.helpers.BTNDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.btn._
import uk.gov.hmrc.pillar2externalteststub.repositories.BTNSubmissionRepository

import java.time.LocalDate
import scala.concurrent.Future

class BTNControllerSpec extends BTNDataFixture with MockitoSugar {

  private val mockRepository = mock[BTNSubmissionRepository]

  "Below Threshold Notification" - {
    "when submitting a notification" - {
      "should return CREATED with success response for a valid submission" in {
        when(mockRepository.insert(any[String], any[BTNRequest])).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validRequest)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined shouldBe true
      }

      "should return UNPROCESSABLE_ENTITY when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/RESTAdapter/PLR/below-threshold-notification")
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(validRequestBody)

        val result = route(app, request).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "002"
      }

      "should return UNPROCESSABLE_ENTITY when accounting period is invalid" in {
        val invalidRequest = validRequest.copy(
          accountingPeriodFrom = LocalDate.of(2024, 12, 31),
          accountingPeriodTo = LocalDate.of(2024, 1, 1)
        )

        val result = route(app, createRequestWithBody(validPlrId, invalidRequest)).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "003"
      }

      "should return BAD_REQUEST when request body is invalid JSON" in {
        val result = route(app, createRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        status(result) shouldBe BAD_REQUEST
        val json = contentAsJson(result)
        (json \ "error" \ "code").as[String] shouldBe "400"
      }

      "should return INTERNAL_SERVER_ERROR when specific Pillar2 ID indicates server error" in {
        val result = route(app, createRequestWithBody(serverErrorPlrId, validRequest)).get
        status(result) shouldBe INTERNAL_SERVER_ERROR
        val json = contentAsJson(result)
        (json \ "error" \ "code").as[String] shouldBe "500"
      }
    }
  }
}
