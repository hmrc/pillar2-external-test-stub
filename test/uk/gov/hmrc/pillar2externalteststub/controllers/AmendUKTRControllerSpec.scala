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
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time._
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future

class AmendUKTRControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues
    with UKTRDataFixture {

  private val mockRepository          = mock[UKTRSubmissionRepository]
  private val mockOrganisationService = mock[OrganisationService]

  private def createTestOrganisation(startDate: String, endDate: String): TestOrganisation = {
    val request = TestOrganisationRequest(
      orgDetails = OrgDetails(
        domesticOnly = true,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(endDate)
      )
    )
    TestOrganisation.fromRequest(request)
  }

  private def createTestOrganisationWithId(plrId: String, startDate: String, endDate: String): TestOrganisationWithId =
    createTestOrganisation(startDate, endDate).withPillar2Id(plrId)

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[UKTRSubmissionRepository].toInstance(mockRepository))
      .overrides(inject.bind[OrganisationService].toInstance(mockOrganisationService))
      .build()

  override def beforeEach(): Unit = {
    reset(mockRepository)
    reset(mockOrganisationService)
    setupDefaultMockBehavior()
  }

  private def setupDefaultMockBehavior(): Unit = {
    when(mockRepository.findDuplicateSubmission(any[String], any[LocalDate], any[LocalDate])).thenReturn(Future.successful(false))
    when(mockRepository.insert(any[UKTRSubmission], any[String], any[Boolean])).thenReturn(Future.successful(true))
    when(mockRepository.findByPillar2Id(any[String])).thenReturn(
      Future.successful(
        Some(
          UKTRMongoSubmission(
            _id = new ObjectId(),
            pillar2Id = validPlrId,
            isAmendment = false,
            data = liabilitySubmission,
            submittedAt = Instant.now()
          )
        )
      )
    )
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))
    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
    )
    ()
  }

  "return OK with success response for a valid uktr amendment" in {
    val originalSubmission = UKTRMongoSubmission(
      _id = new ObjectId(),
      pillar2Id = validPlrId,
      isAmendment = false,
      data = liabilitySubmission,
      submittedAt = Instant.now()
    )

    when(mockRepository.findByPillar2Id(validPlrId)).thenReturn(Future.successful(Some(originalSubmission)))
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenAnswer { invocation =>
      val submission = invocation.getArgument[UKTRSubmission](0)
      val pillar2Id  = invocation.getArgument[String](1)

      // Verify the amendment is created with isAmendment=true
      mockRepository
        .insert(submission, pillar2Id, isAmendment = true)
        .map(Right(_))(scala.concurrent.ExecutionContext.global)
    }

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "chargeReference").as[String] mustEqual "XTC01234123412"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY when X-Pillar2-Id header is missing" in {
    val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader)
      .withBody(Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "002"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "PLR Reference is missing or invalid"
  }

  "return UNPROCESSABLE_ENTITY when subscription is not found for the given PLR reference" in {
    val nonExistentPlrId = "XEPLR5555555554"
    val request          = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "007"
    (jsonResult \ "errors" \ "text").as[String] mustEqual s"No active subscription found for PLR Reference: $nonExistentPlrId"
  }

  "return UNPROCESSABLE_ENTITY when amendment to a liability return that does not exist" in {
    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(None))
    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
    )

    val request = createRequest(validPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
  }

  "return OK with success response for a valid NIL_RETURN amendment" in {
    when(mockRepository.findByPillar2Id(any[String])).thenReturn(
      Future.successful(
        Some(
          UKTRMongoSubmission(
            _id = new ObjectId(),
            pillar2Id = validPlrId,
            isAmendment = false,
            data = nilSubmission,
            submittedAt = Instant.now()
          )
        )
      )
    )
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))
    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
    )

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004321"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY when amendment to a nil return that does not exist" in {
    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(None))
    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
    )

    val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
  }

  "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
    val request = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

    val result = route(app, request).value
    status(result) mustBe INTERNAL_SERVER_ERROR
    val jsonResult = contentAsJson(result)
    (jsonResult \ "error" \ "code").as[String] mustEqual "500"
    (jsonResult \ "error" \ "message").as[String] mustEqual "Internal server error"
  }

  "return BAD_REQUEST for invalid JSON structure" in {
    val invalidJson = Json.obj("invalidField" -> "value")
    val request     = createRequest(validPlrId, invalidJson)

    val result = route(app, request).value
    status(result) mustBe BAD_REQUEST
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
    val emptyLiabilityData = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr())))

    val request = createRequest(validPlrId, Json.toJson(emptyLiabilityData))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "liabilityEntity cannot be empty"
  }

  "verify amendment data is correctly stored" in {
    // Create original submission with initial liability
    val originalMongoSubmission = UKTRMongoSubmission(
      _id = new ObjectId(),
      pillar2Id = validPlrId,
      isAmendment = false,
      data = liabilitySubmission,
      submittedAt = Instant.now()
    )

    val capturedSubmission = new AtomicReference[UKTRSubmission]()

    when(mockRepository.findByPillar2Id(validPlrId)).thenReturn(Future.successful(Some(originalMongoSubmission)))
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenAnswer { invocation =>
      val submission = invocation.getArgument[UKTRSubmission](0)
      capturedSubmission.set(submission)
      Future.successful(Right(true))
    }

    // Create amended submission with updated liability
    val amendedRequestBody = validRequestBody.as[JsObject] ++ Json.obj(
      "liabilities" -> Json.obj(
        "electionDTTSingleMember"  -> false,
        "electionUTPRSingleMember" -> false,
        "numberSubGroupDTT"        -> 4,
        "numberSubGroupUTPR"       -> 5,
        "totalLiability"           -> 6000.99,
        "totalLiabilityDTT"        -> 6000.99,
        "totalLiabilityIIR"        -> 0,
        "totalLiabilityUTPR"       -> 0,
        "liableEntities" -> Json.arr(
          Json.obj(
            "ukChargeableEntityName" -> "Domestic Test Company",
            "idType"                 -> "CRN",
            "idValue"                -> "1234",
            "amountOwedDTT"          -> 6000.99,
            "amountOwedIIR"          -> 0,
            "amountOwedUTPR"         -> 0
          )
        )
      )
    )

    val request = createRequest(validPlrId, amendedRequestBody)
    val result  = route(app, request).value

    status(result) mustBe OK
    Option(capturedSubmission.get()).isDefined mustBe true

    val capturedLiabilityReturn = capturedSubmission.get().asInstanceOf[UKTRLiabilityReturn]
    capturedLiabilityReturn.liabilities.totalLiability mustBe BigDecimal(6000.99)
    capturedLiabilityReturn.liabilities.totalLiabilityDTT mustBe BigDecimal(6000.99)
    capturedLiabilityReturn.liabilities.liableEntities.head.amountOwedDTT mustBe BigDecimal(6000.99)
  }
}
