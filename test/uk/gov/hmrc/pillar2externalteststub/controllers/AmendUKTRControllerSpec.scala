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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
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
import uk.gov.hmrc.pillar2externalteststub.models.error._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.{Instant, LocalDate, ZonedDateTime}
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.Future

class AmendUKTRControllerSpec
    extends AnyWordSpec
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with BeforeAndAfterEach
    with OptionValues
    with UKTRDataFixture
    with ScalaFutures {

  implicit val defaultPatience: PatienceConfig = PatienceConfig(timeout = Span(5, Seconds))

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

  "AmendUKTRController" should {
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

      val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

      val result = route(app, request).value
      status(result) mustBe OK
      val jsonResult = contentAsJson(result)
      (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004321"
      (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
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

    "return UNPROCESSABLE_ENTITY when X-Pillar2-Id header is missing" in {
      val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
        .withHeaders("Content-Type" -> "application/json", authHeader)
        .withBody(Json.toJson(validRequestBody))

      val result = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "002"
    }

    "return UNPROCESSABLE_ENTITY when X-Pillar2-Id format is invalid" in {
      val request = createRequest("invalid-id", Json.toJson(validRequestBody))
      val result  = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "002"
    }

    "return UNPROCESSABLE_ENTITY when subscription is not found" in {
      val nonExistentPlrId = "XEPLR5555555554"
      val request          = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))
      val result           = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "007"
    }

    "return UNPROCESSABLE_ENTITY when amendment to a submission that does not exist" in {
      when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(None))
      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    }

    "return UNPROCESSABLE_ENTITY when accounting period validation fails" in {
      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2025-01-01", "2025-12-31"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    }

    "return UNPROCESSABLE_ENTITY when liableEntities array is empty" in {
      val emptyLiabilityData = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr())))
      val request            = createRequest(validPlrId, Json.toJson(emptyLiabilityData))
      val result             = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
    }

    "return UNPROCESSABLE_ENTITY when database operation fails" in {
      when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(
        Future.failed(DatabaseError("Database connection failed"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
      (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
    }

    "return INTERNAL_SERVER_ERROR for unexpected exceptions" in {
      // Mock a general, unexpected exception during validation
      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
      )
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
      when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(
        Future.failed(new RuntimeException("Unexpected error"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
      val jsonResult = contentAsJson(result)
      (jsonResult \ "error" \ "code").as[String] mustEqual "500"
      (jsonResult \ "error" \ "message").as[String] mustEqual "Internal server error"
    }

    "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
      val request = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value
      status(result) mustBe INTERNAL_SERVER_ERROR
      val jsonResult = contentAsJson(result)
      (jsonResult \ "error" \ "code").as[String] mustEqual "500"
    }

    "return BAD_REQUEST for invalid JSON structure" in {
      val invalidJson = Json.obj("invalidField" -> "value")
      val request     = createRequest(validPlrId, invalidJson)
      val result      = route(app, request).value
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

    "handle validation errors correctly" in {
      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.failed(DatabaseError("Failed to get organisation"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
      (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
    }

    "handle multiple amendment requests correctly" in {
      val originalSubmission = UKTRMongoSubmission(
        _id = new ObjectId(),
        pillar2Id = validPlrId,
        isAmendment = false,
        data = liabilitySubmission,
        submittedAt = Instant.now()
      )

      when(mockRepository.findByPillar2Id(validPlrId)).thenReturn(Future.successful(Some(originalSubmission)))
      when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))

      // Make multiple sequential requests
      val request = createRequest(validPlrId, Json.toJson(validRequestBody))

      // First request
      val result1 = route(app, request).value
      status(result1) mustBe OK
      (contentAsJson(result1) \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"

      // Second request
      val result2 = route(app, request).value
      status(result2) mustBe OK
      (contentAsJson(result2) \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"

      // Third request
      val result3 = route(app, request).value
      status(result3) mustBe OK
      (contentAsJson(result3) \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    }
  }
}
