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

  def request(body: JsValue): FakeRequest[JsValue] =
    createRequest(validPlrId, body)

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
    "return CREATED with success response for a valid uktr amendment" in {
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

        mockRepository
          .insert(submission, pillar2Id, isAmendment = true)
          .map(Right(_))(scala.concurrent.ExecutionContext.global)
      }

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))

      val result = route(app, request).value
      status(result) mustBe CREATED
      val jsonResult = contentAsJson(result)
      (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
      (jsonResult \ "success" \ "chargeReference").as[String] mustEqual "XY123456789012"
      (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
    }

    "return CREATED with success response for a valid NIL_RETURN amendment" in {
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
      status(result) mustBe CREATED
      val jsonResult = contentAsJson(result)
      (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004321"
      (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
    }

    "verify amendment data is correctly stored" in {

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

      status(result) mustBe CREATED
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
      (jsonResult \ "errors" \ "text").as[String] shouldBe "PLR Reference is missing or invalid"
    }

    "return UNPROCESSABLE_ENTITY when X-Pillar2-Id format is invalid" in {
      val request = createRequest("invalid-id", Json.toJson(validRequestBody))
      val result  = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "002"
      (jsonResult \ "errors" \ "text").as[String] shouldBe "PLR Reference is missing or invalid"
    }

    "return UNPROCESSABLE_ENTITY when subscription is not found" in {
      val nonExistentPlrId = "XEPLR5555555554"
      val request          = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))
      val result           = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "007"
      (jsonResult \ "errors" \ "text").as[String] should include(s"No active subscription found for PLR Reference: $nonExistentPlrId")
    }

    "return UNPROCESSABLE_ENTITY when amendment to a submission that does not exist" in {
      when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(None))
      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
      (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
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
      (jsonResult \ "errors" \ "text").as[String] should include("Accounting period")
      (jsonResult \ "errors" \ "text").as[String] should include("does not match the registered period")
    }

    "return UNPROCESSABLE_ENTITY when liableEntities array is empty" in {
      val emptyLiabilityData = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr())))
      val request            = createRequest(validPlrId, Json.toJson(emptyLiabilityData))
      val result             = route(app, request).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
      (jsonResult \ "errors" \ "text").as[String] should include("obligationMTT cannot be true for a domestic-only group")
    }

    "return UNPROCESSABLE_ENTITY when database operation fails" in {
      when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(
        Future.failed(DatabaseError("Database connection failed"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
      val jsonResult = contentAsJson(result)
      (jsonResult \ "code").as[String] mustEqual "DATABASE_ERROR"
      (jsonResult \ "message").as[String] mustEqual "Database connection failed"
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

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))

      val result1 = route(app, request).value
      status(result1) mustBe CREATED
      (contentAsJson(result1) \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"

      val result2 = route(app, request).value
      status(result2) mustBe CREATED
      (contentAsJson(result2) \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"

      val result3 = route(app, request).value
      status(result3) mustBe CREATED
      (contentAsJson(result3) \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    }

    "when submitting a Liability UKTR" should {
      "return CREATED (201) when plrReference is valid and JSON payload is correct" in {
        when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )

        val result = route(app, request(body = validRequestBody)).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XY123456789012"
      }

      "return CREATED (201) when plrReference is valid and JSON is correct and has 3 Liable Entities" in {
        val result = route(
          app,
          request(body =
            validRequestBody.deepMerge(
              Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity, validLiableEntity, validLiableEntity)))
            )
          )
        ).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XY123456789012"
      }

      "return UNPROCESSABLE_ENTITY when obligationMTT is true for domestic-only group with no international entities" in {
        when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )

        val domesticOnlyRequest = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "obligationMTT"     -> true,
              "domesticOnlyGroup" -> true,
              "liableEntities"    -> Json.arr(validLiableEntity)
            )
          )
        )

        val request = createRequest(validPlrId, domesticOnlyRequest)
        val result  = route(app, request).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
        (jsonResult \ "errors" \ "text").as[String] should include("obligationMTT cannot be true for a domestic-only group")
      }

      "return UNPROCESSABLE_ENTITY when obligationMTT is true with non-domestic group but foreign entity indicator is false" in {

        val nonDomesticOrgDetails = OrgDetails(
          domesticOnly = false,
          organisationName = "Non-domestic Org",
          registrationDate = LocalDate.now()
        )

        val nonDomesticAccPeriod = AccountingPeriod(
          startDate = LocalDate.parse("2024-08-14"),
          endDate = LocalDate.parse("2024-12-14")
        )

        val nonDomesticOrgRequest = TestOrganisationRequest(
          orgDetails = nonDomesticOrgDetails,
          accountingPeriod = nonDomesticAccPeriod
        )

        val nonDomesticOrg       = TestOrganisation.fromRequest(nonDomesticOrgRequest)
        val nonDomesticOrgWithId = nonDomesticOrg.withPillar2Id(nonDomesticPlrId)

        when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
          Future.successful(nonDomesticOrgWithId)
        )

        val requestBody = Json.obj(
          "accountingPeriodFrom" -> "2024-08-14",
          "accountingPeriodTo"   -> "2024-12-14",
          "obligationMTT"        -> true,
          "electionUKGAAP"       -> false,
          "liabilities" -> Json.obj(
            "electionDTTSingleMember"  -> false,
            "electionUTPRSingleMember" -> false,
            "numberSubGroupDTT"        -> 4,
            "numberSubGroupUTPR"       -> 5,
            "totalLiability"           -> 10000.99,
            "totalLiabilityDTT"        -> 5000.99,
            "totalLiabilityIIR"        -> 4000,
            "totalLiabilityUTPR"       -> 10000.99,
            "foreignEntityIndicator"   -> false,
            "liableEntities" -> Json.arr(
              Json.obj(
                "ukChargeableEntityName" -> "New Company",
                "idType"                 -> "CRN",
                "idValue"                -> "1234",
                "amountOwedDTT"          -> 12345678901L,
                "amountOwedIIR"          -> 1234567890.09,
                "amountOwedUTPR"         -> 600.5
              )
            )
          )
        )

        val request = createRequest(nonDomesticPlrId, requestBody)
        val result  = route(app, request).value

        status(result) shouldBe CREATED
      }

      "process a NIL return submission properly" in {
        when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )

        val nilReturnSubmission = Json.obj(
          "submissionType" -> "UKTR",
          "accountingPeriod" -> Json.obj(
            "startDate" -> "2024-08-14",
            "endDate"   -> "2024-12-14"
          ),
          "nilReturn" -> true
        )

        val request = createRequest(validPlrId, Json.toJson(nilReturnSubmission))
        val result  = route(app, request).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "handle exceptions from the organisation service correctly" in {

        when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
          Future.failed(new RuntimeException("Database connection error"))
        )

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))
        val result  = route(app, request).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val jsonResult = contentAsJson(result)
        (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
        (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
      }
    }

    "ensure proper error structure for INTERNAL_SERVER_ERROR" in {

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
        Future.failed(new IllegalStateException("Random unexpected error"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe INTERNAL_SERVER_ERROR
      val jsonResult = contentAsJson(result)
      (jsonResult \ "error" \ "code").as[String] mustEqual "500"
      (jsonResult \ "error" \ "message").as[String] mustEqual "Internal server error"
      (jsonResult \ "error" \ "logID").as[String] mustEqual "C0000000000000000000000000000500"
    }

    "process a non-liability return submission correctly" in {
      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
      )

      val customSubmission = Json.obj(
        "submissionType" -> "UKTR",
        "accountingPeriod" -> Json.obj(
          "startDate" -> "2024-08-14",
          "endDate"   -> "2024-12-14"
        ),
        "customField" -> "customValue"
      )

      val request = createRequest(validPlrId, Json.toJson(customSubmission))
      val result  = route(app, request).value

      status(result) mustBe CREATED
      val json = contentAsJson(result)
      (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
      (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
    }

    "successfully handle generic exception in validateAccountingPeriod" in {

      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.failed(DatabaseError("Failed to get organisation"))
      )

      val request = createRequest(validPlrId, Json.toJson(validRequestBody))
      val result  = route(app, request).value

      status(result) mustBe UNPROCESSABLE_ENTITY
      val jsonResult = contentAsJson(result)
      (jsonResult \ "errors" \ "code").as[String] shouldBe "003"
      (jsonResult \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
    }

    "handle non-domestic-only organization with non-liability return" in {

      val nonDomesticOrg = TestOrganisation(
        orgDetails = OrgDetails(
          domesticOnly = false,
          organisationName = "Test Org",
          registrationDate = LocalDate.now()
        ),
        accountingPeriod = AccountingPeriod(
          startDate = LocalDate.parse("2024-08-14"),
          endDate = LocalDate.parse("2024-12-14")
        )
      )

      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.successful(nonDomesticOrg.withPillar2Id(validPlrId))
      )

      val customSubmission = Json.obj(
        "submissionType" -> "UKTR",
        "accountingPeriod" -> Json.obj(
          "startDate" -> "2024-08-14",
          "endDate"   -> "2024-12-14"
        ),
        "customField" -> "customValue"
      )

      val request = createRequest(validPlrId, customSubmission)
      val result  = route(app, request).value

      status(result) mustBe CREATED
      val jsonResult = contentAsJson(result)
      (jsonResult \ "success" \ "formBundleNumber").as[String] shouldBe "119000004320"
    }

    "handle non-domestic-only organization with nil return and obligationMTT=false" in {

      val nonDomesticOrg = TestOrganisation(
        orgDetails = OrgDetails(
          domesticOnly = false,
          organisationName = "Test Org",
          registrationDate = LocalDate.now()
        ),
        accountingPeriod = AccountingPeriod(
          startDate = LocalDate.parse("2024-08-14"),
          endDate = LocalDate.parse("2024-12-14")
        )
      )

      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.successful(nonDomesticOrg.withPillar2Id(validPlrId))
      )

      val requestBody = nilReturnBody(obligationMTT = false, electionUKGAAP = false)

      val request = createRequest(validPlrId, requestBody)
      val result  = route(app, request).value

      status(result) mustBe CREATED
      val jsonResult = contentAsJson(result)
      (jsonResult \ "success" \ "formBundleNumber").as[String] shouldBe "119000004321"
    }

    "handle non-domestic-only organization with liability return and obligationMTT=false" in {

      val nonDomesticOrgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Non-domestic Org",
        registrationDate = LocalDate.now()
      )

      val nonDomesticAccPeriod = AccountingPeriod(
        startDate = LocalDate.parse("2024-08-14"),
        endDate = LocalDate.parse("2024-12-14")
      )

      val nonDomesticOrgRequest = TestOrganisationRequest(
        orgDetails = nonDomesticOrgDetails,
        accountingPeriod = nonDomesticAccPeriod
      )

      val nonDomesticOrg       = TestOrganisation.fromRequest(nonDomesticOrgRequest)
      val nonDomesticOrgWithId = nonDomesticOrg.withPillar2Id(nonDomesticPlrId)

      when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
        Future.successful(nonDomesticOrgWithId)
      )

      when(mockRepository.insert(any[UKTRSubmission], any[String], any[Boolean])).thenReturn(Future.successful(true))

      val requestBody = Json.obj(
        "accountingPeriodFrom" -> "2024-08-14",
        "accountingPeriodTo"   -> "2024-12-14",
        "obligationMTT"        -> false,
        "electionUKGAAP"       -> false,
        "liabilities" -> Json.obj(
          "electionDTTSingleMember"  -> false,
          "electionUTPRSingleMember" -> false,
          "numberSubGroupDTT"        -> 4,
          "numberSubGroupUTPR"       -> 5,
          "totalLiability"           -> 10000.99,
          "totalLiabilityDTT"        -> 5000.99,
          "totalLiabilityIIR"        -> 4000,
          "totalLiabilityUTPR"       -> 10000.99,
          "foreignEntityIndicator"   -> true,
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "New Company",
              "idType"                 -> "CRN",
              "idValue"                -> "1234",
              "amountOwedDTT"          -> 12345678901L,
              "amountOwedIIR"          -> 1234567890.09,
              "amountOwedUTPR"         -> 600.5
            )
          )
        )
      )

      val request = createRequest(nonDomesticPlrId, requestBody)
      val result  = route(app, request).value

      status(result) shouldBe CREATED

      val jsonResult = contentAsJson(result)
      (jsonResult \ "success" \ "formBundleNumber").as[String] shouldBe "119000004320"
      (jsonResult \ "success" \ "chargeReference").as[String]  shouldBe "XY123456789012"
    }
  }
}
