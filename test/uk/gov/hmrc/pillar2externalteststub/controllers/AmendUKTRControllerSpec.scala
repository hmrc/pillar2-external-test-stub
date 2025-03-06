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

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn, UKTRSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time._
import scala.concurrent.Future

class AmendUKTRControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with UKTRDataFixture
    with MockitoSugar
    with BeforeAndAfterEach {

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

  override def beforeEach(): Unit = {
    import org.mockito.Mockito.doReturn

    doReturn(Future.successful(testOrg))
      .when(mockOrgService)
      .getOrganisation(anyString())

    doReturn(
      Future.successful(
        Some(
          UKTRMongoSubmission(
            _id = new ObjectId(),
            pillar2Id = validPlrId,
            isAmendment = false,
            data = Json.fromJson[UKTRSubmission](validRequestBody).get,
            submittedAt = Instant.now()
          )
        )
      )
    ).when(mockRepository).findByPillar2Id(anyString())

    val _ = org.mockito.Mockito
      .when(mockRepository.findByPillar2Id(eqTo("XEPLR5555555554")))
      .thenReturn(Future.successful(None))
  }

  "return OK with success response for a valid uktr amendment" in {
    val _ = when(
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

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage must include("Pillar2 ID Missing or Invalid")
  }

  "return UNPROCESSABLE_ENTITY when subscription is not found for the given PLR reference" in {
    val nonExistentPlrId = "XEPLR5555555554"
    val request          = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage mustBe "Request could not be processed"
  }

  "return UNPROCESSABLE_ENTITY when amendment to a liability return that does not exist" in {
    val specialPlrId = "XEPLR1111111111"

    import org.mockito.Mockito.doReturn
    val failingSubmission = UKTRMongoSubmission(
      _id = new ObjectId(),
      pillar2Id = specialPlrId,
      isAmendment = false,
      data = Json.fromJson[UKTRSubmission](validRequestBody).get,
      submittedAt = Instant.now()
    )

    doReturn(Future.successful(Some(failingSubmission)))
      .when(mockRepository)
      .findByPillar2Id(eqTo(specialPlrId))

    val _ = when(
      mockRepository.update(
        argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
        any[String]
      )
    ).thenThrow(new RuntimeException("Request could not be processed"))

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage mustBe "Request could not be processed"
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
    val specialPlrId = "XEPLR2222222222"

    import org.mockito.Mockito.doReturn
    val failingSubmission = UKTRMongoSubmission(
      _id = new ObjectId(),
      pillar2Id = specialPlrId,
      isAmendment = false,
      data = Json.fromJson[UKTRSubmission](nilReturnBody(obligationMTT = false, electionUKGAAP = false)).get,
      submittedAt = Instant.now()
    )

    doReturn(Future.successful(Some(failingSubmission)))
      .when(mockRepository)
      .findByPillar2Id(eqTo(specialPlrId))

    val _ = when(
      mockRepository.update(
        argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]),
        any[String]
      )
    ).thenThrow(new RuntimeException("Request could not be processed"))

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage mustBe "Request could not be processed"
  }

  "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
    val serverErrorRequest = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

    val exception = intercept[Exception] {
      await(route(app, serverErrorRequest).get)
    }

    exception.getMessage must include("Internal server error")
  }

  "return BAD_REQUEST for invalid JSON structure" in {
    val invalidJson = Json.obj("invalidField" -> "value", "anotherInvalidField" -> 123)
    val request     = createRequest(validPlrId, invalidJson)

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage must include("Bad request")
  }

  "return BAD_REQUEST for unsupported submission type" in {
    val unsupportedSubmissionJson = Json.obj(
      "accountingPeriodFrom" -> "2024-01-01",
      "accountingPeriodTo"   -> "2024-12-31",
      "obligationMTT"        -> false,
      "electionUKGAAP"       -> false,
      "liabilities" -> Json.obj(
        "someUnsupportedField" -> "someValue"
      )
    )

    val request = createRequest(validPlrId, unsupportedSubmissionJson)

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage must include("Bad request")
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

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage must include("Bad request")
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

    val exception = intercept[Exception] {
      await(route(app, request).get)
    }

    exception.getMessage must include("Invalid return")
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

    val exception = intercept[Exception] {
      await(route(app, createRequest(validPlrId, Json.toJson(invalidAmountsBody))).get)
    }

    exception.getMessage mustBe "Invalid total liability"
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

    val exception = intercept[Exception] {
      await(route(app, createRequest(validPlrId, Json.toJson(invalidIdTypeBody))).get)
    }

    exception.getMessage must include("Invalid return")
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

    val exception = intercept[Exception] {
      await(route(app, createRequest(validPlrId, missingRequiredFields)).get)
    }

    exception.getMessage must include("Bad request")
  }
}
