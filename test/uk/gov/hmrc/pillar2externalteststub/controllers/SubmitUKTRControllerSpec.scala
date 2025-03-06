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

import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.{Application, inject}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.FakeRequest
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.{Instant, LocalDate}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import play.api.mvc.Result
import play.api.test.Helpers._
import org.scalatest.compatible.Assertion

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture with MockitoSugar {

  implicit class AwaitFuture(fut: Future[Result]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err mustEqual expected
    }
  }

  private val mockRepository = mock[UKTRSubmissionRepository]
  private val mockOrgService = mock[OrganisationService]

  val orgDetails: OrgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.of(2022, 4, 1)
  )

  val testOrgDetails: TestOrganisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = AccountingPeriod(
      startDate = LocalDate.of(2024, 8, 14),
      endDate = LocalDate.of(2024, 12, 14)
    ),
    lastUpdated = Instant.now()
  )

  val testOrg: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = validPlrId,
    organisation = testOrgDetails
  )

  when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrg))
  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      inject.bind[UKTRSubmissionRepository].toInstance(mockRepository),
      inject.bind[OrganisationService].toInstance(mockOrgService)
    )
    .build()

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest("POST", routes.SubmitUKTRController.submitUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  "SubmitUKTRController" - {
    "return ETMPBadRequest when invalid JSON is submitted" in {
      val invalidJsonBody = Json.obj(
        "someField" -> "someValue"
      )
      val request = createRequest(validPlrId, invalidJsonBody)

      route(app, request).value shouldFailWith ETMPBadRequest
    }

    "return CREATED with success response for a valid liability submission" in {
      when(mockRepository.insert(any[UKTRSubmission](), eqTo(validPlrId), eqTo(false))).thenReturn(Future.successful(true))

      val request = createRequest(validPlrId, validRequestBody)

      val result = route(app, request).value
      status(result) mustBe CREATED
      contentAsJson(result) mustEqual Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)
    }

    "return CREATED with success response for a valid NIL return submission" in {
      when(mockRepository.insert(any[UKTRSubmission](), eqTo(validPlrId), eqTo(false))).thenReturn(Future.successful(true))

      val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

      val result = route(app, request).value
      status(result) mustBe CREATED
      contentAsJson(result) mustEqual Json.toJson(NilReturnSuccess.successfulNilReturnResponse)
    }

    "return Pillar2IdMissing when X-Pillar2-Id header is missing" in {
      val request = FakeRequest("POST", routes.SubmitUKTRController.submitUKTR.url)
        .withHeaders(authHeader)
        .withBody(validRequestBody)

      route(app, request).value shouldFailWith Pillar2IdMissing
    }

    "return InvalidReturn if accounting period doesn't match" in {
      val invalidAccountingPeriodBody = validRequestBody.deepMerge(
        Json.obj(
          "accountingPeriodFrom" -> "2024-01-01",
          "accountingPeriodTo"   -> "2024-12-31"
        )
      )
      val request = createRequest(validPlrId, invalidAccountingPeriodBody)

      route(app, request).value shouldFailWith InvalidReturn
    }

    "return ETMPInternalServerError for specific Pillar2Id" in {
      val request = createRequest(ServerErrorPlrId, validRequestBody)

      route(app, request).value shouldFailWith ETMPInternalServerError
    }

    "return FORBIDDEN when missing Authorization header" in {
      val request = FakeRequest("POST", routes.SubmitUKTRController.submitUKTR.url)
        .withHeaders("X-Pillar2-Id" -> validPlrId)
        .withBody(validRequestBody)

      val result = route(app, request).value
      status(result) mustBe FORBIDDEN
    }

    "return InvalidTotalLiability when submitting with invalid amounts" in {
      val invalidAmountsBody: JsValue = validRequestBody.deepMerge(
        Json.obj(
          "liabilities" -> Json.obj(
            "totalLiability"    -> -500,
            "totalLiabilityDTT" -> 10000000000000.99
          )
        )
      )
      val request = createRequest(validPlrId, invalidAmountsBody)

      route(app, request).value shouldFailWith InvalidTotalLiability
    }
  }
}
