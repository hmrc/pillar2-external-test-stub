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

import org.mockito.ArgumentMatchers.{any, anyString, eq as eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{IdMissingOrInvalid, NoDataFound, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.{HIPBadRequest, OrganisationNotFound, TestDataNotFound}
import uk.gov.hmrc.pillar2externalteststub.services.{AccountActivityService, OrganisationService}

import scala.concurrent.Future

class AccountActivityControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with OptionValues
    with MockitoSugar
    with TestOrgDataFixture {

  private val mockAccountService = mock[AccountActivityService]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        bind[OrganisationService].toInstance(mockOrgService),
        bind[AccountActivityService].toInstance(mockAccountService)
      )
      .build()

  def createRequest(
    plrId:    String = validPlrId,
    fromDate: String = accountingPeriod.startDate.toString,
    toDate:   String = accountingPeriod.endDate.toString
  ): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, routes.AccountActivityController.get(fromDate, toDate).url)
      .withHeaders(hipHeaders :+ accountActivityHeader :+ ("X-Pillar2-Id" -> plrId)*)

  "Account Activity" - {
    "when requesting account activity" - {

      "should return OK with successful response when data exists" in {
        val expectedJson = Json.obj("processingDate" -> "2024-01-01T00:00:00Z")

        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(organisationWithId))

        when(mockAccountService.getAccountActivity(eqTo(organisationWithId)))
          .thenReturn(Future.successful(expectedJson))

        val result = route(app, createRequest()).value
        status(result) mustBe OK
        contentAsJson(result) mustBe expectedJson
      }

      "should return NoDataFound (404) when organisation is not found" in {
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

        route(app, createRequest()).value shouldFailWith NoDataFound
      }

      "should return TestDataNotFound (404) when test data is missing" in {
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(organisationWithId))

        when(mockAccountService.getAccountActivity(any()))
          .thenReturn(Future.failed(TestDataNotFound(validPlrId)))

        route(app, createRequest()).value shouldFailWith TestDataNotFound(validPlrId)
      }

      "should return RequestCouldNotBeProcessed (422) for invalid date format" in {
        route(app, createRequest(fromDate = "invalid-date")).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should return RequestCouldNotBeProcessed (422) when fromDate is after toDate" in {
        val futureFrom = "2025-01-01"
        val pastTo     = "2024-01-01"

        route(app, createRequest(fromDate = futureFrom, toDate = pastTo)).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should fail if X-Pillar2-Id header is missing" in {
        val req = FakeRequest(GET, routes.AccountActivityController.get("2024-01-01", "2024-12-31").url)
          .withHeaders(hipHeaders :+ accountActivityHeader*)
        val result = route(app, req).value

        result shouldFailWith IdMissingOrInvalid
      }

      "should fail if X-Message-Type header is missing" in {
        val req = FakeRequest(GET, routes.AccountActivityController.get("2024-01-01", "2024-12-31").url)
          .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> validPlrId)*)
        val result = route(app, req).value

        result shouldFailWith HIPBadRequest()
      }

    }
  }
}
