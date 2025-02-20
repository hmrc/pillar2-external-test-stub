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
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.repositories.BTNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate
import scala.concurrent.Future

class BTNControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with BTNDataFixture with TestOrgDataFixture {

  private val mockRepository = mock[BTNSubmissionRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .overrides(inject.bind[BTNSubmissionRepository].toInstance(mockRepository))
      .build()

  "Below Threshold Notification" - {
    "when submitting a notification" - {
      "should return CREATED with success response for a valid submission" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockRepository.findByPillar2Id(eqTo(validPlrId))).thenReturn(Future.successful(Seq.empty))
        when(mockRepository.insert(eqTo(validPlrId), any[BTNRequest])).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
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
        (json \ "errors" \ "text").as[String] shouldBe "Pillar2 ID is missing or invalid"
      }

      "should return UNPROCESSABLE_ENTITY when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createRequestWithBody(invalidPlrId, validBTNRequest)).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "002"
        (json \ "errors" \ "text").as[String] shouldBe "Pillar2 ID is missing or invalid"
      }

      "should return UNPROCESSABLE_ENTITY when organisation not found" in {
        when(mockOrgService.getOrganisation(any[String]))
          .thenReturn(Future.failed(OrganisationNotFound("Organisation not found")))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "007"
        (json \ "errors" \ "text").as[String] shouldBe "Business Partner does not have an Active Pillar 2 registration"
      }

      "should return UNPROCESSABLE_ENTITY when the accounting period doesn't match organisation's" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

        val mismatchedRequest = validBTNRequest.copy(
          accountingPeriodFrom = LocalDate.of(2024, 2, 1),
          accountingPeriodTo = LocalDate.of(2024, 12, 31)
        )

        when(mockOrgService.getOrganisation(validPlrId))
          .thenReturn(Future.successful(organisationWithId))

        val result = route(app, createRequestWithBody(validPlrId, mismatchedRequest)).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "003"
        (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed or invalid"
      }

      "should return UNPROCESSABLE_ENTITY when duplicate submission exists" in {
        when(mockOrgService.getOrganisation(validPlrId)).thenReturn(Future.successful(organisationWithId))
        when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Seq(BTNMongoSubmission)))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "004"
        (json \ "errors" \ "text").as[String] shouldBe "Duplicate Submission"
      }

      "should return UNPROCESSABLE_ENTITY when accounting period is invalid" in {
        val invalidBTNRequest = validBTNRequest.copy(
          accountingPeriodFrom = LocalDate.of(2024, 12, 31),
          accountingPeriodTo = LocalDate.of(2024, 1, 1)
        )

        val result = route(app, createRequestWithBody(validPlrId, invalidBTNRequest)).get
        status(result) shouldBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] shouldBe "003"
        (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed or invalid"
      }

      "should return BAD_REQUEST when request body is invalid JSON" in {
        val result = route(app, createRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        status(result) shouldBe BAD_REQUEST
        val json = contentAsJson(result)
        (json \ "error" \ "code").as[String]    shouldBe "400"
        (json \ "error" \ "message").as[String] shouldBe "Invalid request payload"
      }

      "should return INTERNAL_SERVER_ERROR when specific Pillar2 ID indicates server error" in {
        val result = route(app, createRequestWithBody(serverErrorPlrId, validBTNRequest)).get
        status(result) shouldBe INTERNAL_SERVER_ERROR
        val json = contentAsJson(result)
        (json \ "error" \ "code").as[String]    shouldBe "500"
        (json \ "error" \ "message").as[String] shouldBe "Internal server error"
      }
    }
  }
}
