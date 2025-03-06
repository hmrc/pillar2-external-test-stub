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

import org.mockito.ArgumentMatchers._
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.OptionValues
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn, UKTRSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import java.time._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import org.scalatest.compatible.Assertion

class AmendUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture with MockitoSugar {

  implicit class AwaitFuture(fut: Future[Result]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err mustEqual expected
    }
  }

  private val mockRepository = mock[UKTRSubmissionRepository]
  private val mockOrgService = mock[OrganisationService]

  private val orgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.now()
  )

  override val accountingPeriod: AccountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 8, 14),
    endDate = LocalDate.of(2024, 12, 14)
  )

  private val testOrgDetails = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    lastUpdated = Instant.now()
  )

  private val testOrg = TestOrganisationWithId(
    pillar2Id = validPlrId,
    organisation = testOrgDetails
  )

  when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrg))
  when(mockRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[UKTRSubmissionRepository].toInstance(mockRepository),
        inject.bind[OrganisationService].toInstance(mockOrgService)
      )
      .build()

  "return OK with success response for a valid uktr amendment" in {
    when(
      mockRepository.update(
        argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
        any[String]
      )
    ).thenReturn(Future.successful(Right(true)))

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "chargeReference").as[String] mustEqual "XTC01234123412"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return BAD_REQUEST when X-Pillar2-Id header is missing" in {
    val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader)
      .withBody(Json.toJson(validRequestBody))

    route(app, request).value shouldFailWith Pillar2IdMissing
  }

  "return NoActiveSubscription when subscription is not found for the given PLR reference" in {
    when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))
    val nonExistentPlrId = "XEPLR5555555554"

    when(
      mockRepository.update(
        argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
        any[String]
      )
    ).thenReturn(Future.successful(Right(true)))

    val request = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))

    route(app, request).value shouldFailWith NoActiveSubscription
  }

  "return UNPROCESSABLE_ENTITY when amendment to a liability return that does not exist" in {
    when(
      mockRepository.update(
        argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
        any[String]
      )
    ).thenReturn(Future.failed(RequestCouldNotBeProcessed))

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    route(app, request).value shouldFailWith RequestCouldNotBeProcessed
  }

  "return OK with success response for a valid NIL_RETURN amendment" in {
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]), any[String]))
      .thenReturn(Future.successful(Right(true)))

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY when amendment to a nil return that does not exist" in {
    when(
      mockRepository.update(
        argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]),
        any[String]
      )
    ).thenReturn(Future.failed(RequestCouldNotBeProcessed))

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    route(app, request).value shouldFailWith RequestCouldNotBeProcessed
  }

  "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
    val serverErrorRequest = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

    route(app, serverErrorRequest).value shouldFailWith ETMPInternalServerError
  }

  "return BAD_REQUEST for invalid JSON structure" in {
    val invalidJson = Json.obj("invalidField" -> "value", "anotherInvalidField" -> 123)
    val request     = createRequest(validPlrId, invalidJson)

    route(app, request).value shouldFailWith ETMPBadRequest
  }

  "return BAD_REQUEST for custom UKTRSubmission type that's neither UKTRNilReturn nor UKTRLiabilityReturn" in {
    val customUnsupportedSubmissionJson = Json.obj(
      "accountingPeriodFrom" -> "2024-01-01",
      "accountingPeriodTo"   -> "2024-12-31",
      "obligationMTT"        -> false,
      "electionUKGAAP"       -> false,
      "liabilities" -> Json.obj(
        "customField" -> "customValue"
      )
    )

    val request = createRequest(validPlrId, customUnsupportedSubmissionJson)

    route(app, request).value shouldFailWith ETMPBadRequest
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
    val emptyLiabilityData = validRequestBody.deepMerge(
      Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr()))
    )

    val request = createRequest(validPlrId, Json.toJson(emptyLiabilityData))

    route(app, request).value shouldFailWith InvalidReturn
  }

  "return UNPROCESSABLE_ENTITY when amending with invalid amounts" in {
    val invalidAmountsBody = validRequestBody.deepMerge(
      Json.obj(
        "liabilities" -> Json.obj(
          "totalLiability"    -> -500,
          "totalLiabilityDTT" -> 10000000000000.99
        )
      )
    )

    route(app, createRequest(validPlrId, Json.toJson(invalidAmountsBody))).value shouldFailWith InvalidTotalLiability
  }

  "return UNPROCESSABLE_ENTITY when amending with invalid ID type" in {
    val invalidIdTypeBody = validRequestBody.deepMerge(
      Json.obj(
        "liabilities" -> Json.obj(
          "liableEntities" -> Json.arr(
            validLiableEntity.as[JsObject] ++ Json.obj("idType" -> "INVALID")
          )
        )
      )
    )

    route(app, createRequest(validPlrId, Json.toJson(invalidIdTypeBody))).value shouldFailWith InvalidReturn
  }

  "return FORBIDDEN when missing Authorization header" in {
    val requestWithoutAuth = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", "X-Pillar2-Id" -> validPlrId)
      .withBody(Json.toJson(validRequestBody))

    val result = route(app, requestWithoutAuth).value
    status(result) mustBe FORBIDDEN
  }

  "return BAD_REQUEST when required fields are missing" in {
    val missingRequiredFields = Json.obj(
      "accountingPeriodFrom" -> "2024-08-14",
      "obligationMTT"        -> false,
      "electionUKGAAP"       -> false
    )

    route(app, createRequest(validPlrId, missingRequiredFields)).value shouldFailWith ETMPBadRequest
  }
}
