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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.compatible.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.{ORNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.repositories.ORNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.{ORNService, OrganisationService}

import java.time.LocalDate
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration._

class ORNControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ORNDataFixture with TestOrgDataFixture {

  implicit class AwaitFuture(fut: Future[Result]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err shouldBe expected
    }
  }

  private val mockRepository = mock[ORNSubmissionRepository]
  private val mockORNService = mock[ORNService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .overrides(inject.bind[ORNSubmissionRepository].toInstance(mockRepository))
      .overrides(inject.bind[ORNService].toInstance(mockORNService))
      .build()

  "Overseas Return Notification" - {
    "when submitting an ORN" - {
      "should return CREATED with success response for a valid submission" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.submitORN(eqTo(validPlrId), any[ORNRequest])).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
      }

      "should return Pillar2IdMissing when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/RESTAdapter/PLR/overseas-return-notification")
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(validRequestBody)

        val result = route(app, request).get
        result shouldFailWith Pillar2IdMissing
      }

      "should return Pillar2IdMissing when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createRequestWithBody(invalidPlrId, validORNRequest)).get
        result shouldFailWith Pillar2IdMissing
      }

      "should return NoActiveSubscription when organisation not found" in {
        when(mockOrgService.getOrganisation(any[String]))
          .thenReturn(Future.failed(OrganisationNotFound("Organisation not found")))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest)).get
        result shouldFailWith NoActiveSubscription
      }

      "should return ETMPBadRequest when request body is invalid JSON" in {
        val result = route(app, createSubmitRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest
      }

      "should return ETMPInternalServerError when specific Pillar2 ID indicates server error" in {
        val serverErrorPlrId = "XEPLR5000000000"
        val result           = route(app, createRequestWithBody(serverErrorPlrId, validORNRequest)).get
        result shouldFailWith ETMPInternalServerError
      }
    }

    "when amending an ORN" - {
      "should return OK with success response for a valid amendment" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.amendORN(eqTo(validPlrId), any[ORNRequest])).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest, isAmend = true)).get
        status(result) shouldBe OK
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
      }

      "should return UNPROCESSABLE_ENTITY when organisation not found during amendment" in {
        when(mockOrgService.getOrganisation(any[String]))
          .thenReturn(Future.failed(OrganisationNotFound("Organisation not found")))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest, isAmend = true)).get
        result shouldFailWith NoActiveSubscription
      }

      "should return BAD_REQUEST when amendment request body is invalid JSON" in {
        val result = route(app, createAmendRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest
      }
    }

    "when retrieving an ORN" - {
      "should return OK with submission details for a valid request" in {
        val fromDate = "2024-01-01"
        val toDate   = "2024-12-31"

        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.getORN(eqTo(validPlrId), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Some(ornMongoSubmission)))

        val request = FakeRequest(GET, s"/RESTAdapter/PLR/overseas-return-notification?accountingPeriodFrom=$fromDate&accountingPeriodTo=$toDate")
          .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> validPlrId)

        val result = route(app, request).get
        status(result) shouldBe OK
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined shouldBe true
        (json \ "success" \ "accountingPeriodFrom").as[String]        shouldBe fromDate
        (json \ "success" \ "accountingPeriodTo").as[String]          shouldBe toDate
        (json \ "success" \ "countryGIR").as[String]                  shouldBe "US"
      }

      "should return RequestCouldNotBeProcessed when no submission is found" in {
        val fromDate = "2024-01-01"
        val toDate   = "2024-12-31"

        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.getORN(eqTo(validPlrId), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))

        val request = FakeRequest(GET, s"/RESTAdapter/PLR/overseas-return-notification?accountingPeriodFrom=$fromDate&accountingPeriodTo=$toDate")
          .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> validPlrId)

        val result = route(app, request).get
        result shouldFailWith RequestCouldNotBeProcessed
      }

      "should return ETMPBadRequest when date format is invalid" in {
        val fromDate = "invalid-date"
        val toDate   = "2024-12-31"

        val request = FakeRequest(GET, s"/RESTAdapter/PLR/overseas-return-notification?accountingPeriodFrom=$fromDate&accountingPeriodTo=$toDate")
          .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> validPlrId)

        val result = route(app, request).get
        result shouldFailWith ETMPBadRequest
      }
    }
  }
}
