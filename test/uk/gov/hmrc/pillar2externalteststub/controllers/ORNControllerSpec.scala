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
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.{ORNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.services.{ORNService, OrganisationService}

import java.time.LocalDate
import scala.concurrent.Future
class ORNControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with ORNDataFixture with TestOrgDataFixture {

  private val mockORNService = mock[ORNService]
  private val mockAppConfig  = mock[AppConfig]

  when(mockAppConfig.countryList).thenReturn(Set(validORNRequest.countryGIR, validORNRequest.issuingCountryTIN))

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .overrides(inject.bind[ORNService].toInstance(mockORNService))
      .overrides(inject.bind[AppConfig].toInstance(mockAppConfig))
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

      "should return IdMissingOrInvalid when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/RESTAdapter/plr/overseas-return-notification")
          .withHeaders(hipHeaders: _*)
          .withBody(validRequestBody)

        val result = route(app, request).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return IdMissingOrInvalid when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createRequestWithBody(invalidPlrId, validORNRequest)).get
        result shouldFailWith IdMissingOrInvalid
      }

      "should return NoActiveSubscription when organisation not found" in {
        when(mockOrgService.getOrganisation(any[String]))
          .thenReturn(Future.failed(OrganisationNotFound("Organisation not found")))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest)).get
        result shouldFailWith NoActiveSubscription
      }

      "should return ETMPBadRequest when request body is invalid JSON" in {
        val result = route(app, createSubmitRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest()
      }

      "should return ETMPInternalServerError when specific Pillar2 ID indicates server error" in {
        val result = route(app, createRequestWithBody(serverErrorPlrId, validORNRequest)).get
        result shouldFailWith ETMPInternalServerError
      }

      "should return InvalidReturn when the submission's accounting period does not match that of the testOrg" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

        val result = route(app, createRequestWithBody(validPlrId, differentAccountingPeriodORNRequest)).get
        result shouldFailWith InvalidReturn
      }

      "should return RequestCouldNotBeProcessed" - {
        "when countryGIR is not a valid ISO country code" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

          val result = route(app, createRequestWithBody(validPlrId, validORNRequest.copy(countryGIR = "ZZ"))).get
          result shouldFailWith RequestCouldNotBeProcessed
        }

        "when issuingCountryTIN is not a valid ISO country code" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

          val result = route(app, createRequestWithBody(validPlrId, validORNRequest.copy(issuingCountryTIN = "ZZ"))).get
          result shouldFailWith RequestCouldNotBeProcessed
        }
      }
    }

    "when amending an ORN" - {
      "should return OK with success response for a valid amendment" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(nonDomesticOrganisation))
        when(mockORNService.amendORN(eqTo(validPlrId), any[ORNRequest])).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest, isAmend = true)).get
        status(result) shouldBe OK
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined   shouldBe true
        (json \ "success" \ "formBundleNumber").asOpt[String].isDefined shouldBe true
      }

      "should return NoActiveSubscription when organisation not found during amendment" in {
        when(mockOrgService.getOrganisation(any[String]))
          .thenReturn(Future.failed(OrganisationNotFound("Organisation not found")))

        val result = route(app, createRequestWithBody(validPlrId, validORNRequest, isAmend = true)).get
        result shouldFailWith NoActiveSubscription
      }

      "should return ETMPBadRequest when amendment request body is invalid JSON" in {
        val result = route(app, createAmendRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest()
      }

      "should return InvalidReturn when the amendments's accounting period does not match that of the testOrg" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

        val result = route(app, createRequestWithBody(validPlrId, differentAccountingPeriodORNRequest, isAmend = true)).get
        result shouldFailWith InvalidReturn
      }

      "should return RequestCouldNotBeProcessed" - {
        "when countryGIR is not a valid ISO country code" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

          val result = route(app, createRequestWithBody(validPlrId, validORNRequest.copy(countryGIR = "ZZ"), isAmend = true)).get
          result shouldFailWith RequestCouldNotBeProcessed
        }

        "when issuingCountryTIN is not a valid ISO country code" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

          val result = route(app, createRequestWithBody(validPlrId, validORNRequest.copy(issuingCountryTIN = "ZZ"), isAmend = true)).get
          result shouldFailWith RequestCouldNotBeProcessed
        }
      }
    }

    "when retrieving an ORN" - {
      "should return OK with submission details for a valid request" in {
        val fromDate = "2024-01-01"
        val toDate   = "2024-12-31"

        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.getORN(eqTo(validPlrId), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Some(ornMongoSubmission)))

        val result = route(app, getORNRequest(validPlrId, fromDate, toDate)).get
        status(result) shouldBe OK
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined shouldBe true
        (json \ "success" \ "accountingPeriodFrom").as[String]        shouldBe fromDate
        (json \ "success" \ "accountingPeriodTo").as[String]          shouldBe toDate
        (json \ "success" \ "countryGIR").as[String]                  shouldBe "US"
      }

      "should return NoFormBundleFound when no submission is found" in {
        val fromDate = "2024-01-01"
        val toDate   = "2024-12-31"

        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.getORN(eqTo(validPlrId), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))

        val result = route(app, getORNRequest(validPlrId, fromDate, toDate)).get
        result shouldFailWith NoFormBundleFound
      }

      "should return RequestCouldNotBeProcessed when the date range is invalid" in {
        val fromDate = "2024-01-01"
        val toDate   = "2023-12-31"

        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockORNService.getORN(eqTo(validPlrId), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))

        val result = route(app, getORNRequest(validPlrId, fromDate, toDate)).get
        result shouldFailWith RequestCouldNotBeProcessed
      }

      "should return RequestCouldNotBeProcessed if the test organisation is domestic-only" in {
        val fromDate = "2024-01-01"
        val toDate   = "2024-12-31"

        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(domesticOrganisation))
        when(mockORNService.getORN(eqTo(validPlrId), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))

        val result = route(app, getORNRequest(validPlrId, fromDate, toDate)).get
        result shouldFailWith RequestCouldNotBeProcessed
      }

      "should return ETMPBadRequest when date format is invalid" in {
        val fromDate = "invalid-date"
        val toDate   = "2024-12-31"

        val result = route(app, getORNRequest(validPlrId, fromDate, toDate)).get
        result shouldFailWith ETMPBadRequest()
      }
    }
  }
}
