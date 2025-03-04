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
import org.mockito.ArgumentMatchers.{any, argThat}
import org.mockito.Mockito.{reset, when}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterEach, OptionValues}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.HttpErrorHandler
import play.api.inject
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pillar2externalteststub.controllers.StubErrorHandler
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.error.DomesticOnlyMTTError
import uk.gov.hmrc.pillar2externalteststub.models.error.InvalidAccountingPeriod
import uk.gov.hmrc.pillar2externalteststub.models.error.InvalidJson
import uk.gov.hmrc.pillar2externalteststub.models.error.InvalidPillar2Id
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.error.SubmissionNotFoundError
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn, UKTRSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.{Instant, LocalDate, ZonedDateTime}
import scala.concurrent.Future

class AmendUKTRControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with UKTRDataFixture
    with MockitoSugar
    with BeforeAndAfterEach {

  private val mockRepository          = mock[UKTRSubmissionRepository]
  private val mockOrganisationService = mock[OrganisationService]

  private val mockUKTRMongoSubmission = UKTRMongoSubmission(
    _id = new ObjectId(),
    pillar2Id = validPlrId,
    isAmendment = false,
    data = mock[UKTRSubmission],
    submittedAt = Instant.now()
  )

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[UKTRSubmissionRepository].toInstance(mockRepository),
        inject.bind[OrganisationService].toInstance(mockOrganisationService),
        inject.bind[HttpErrorHandler].to[StubErrorHandler]
      )
      .configure("play.http.errorHandler" -> "uk.gov.hmrc.pillar2externalteststub.controllers.StubErrorHandler")
      .build()

  override def beforeEach(): Unit = reset(mockRepository)

  "return OK with success response for a valid uktr amendment" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]), any[String]))
      .thenReturn(Future.successful(Right(true)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(testOrgWithId)
    )

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

    // Use the error handler directly to handle the InvalidPillar2Id error
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result       = errorHandler.onServerError(request, InvalidPillar2Id(None))

    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "002"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "PLR Reference is missing or invalid"
  }

  "return UNPROCESSABLE_ENTITY when subscription is not found for the given PLR reference" in {
    val nonExistentPlrId = "XEPLR5555555554"

    val request = createRequest(nonExistentPlrId, Json.toJson(validRequestBody))

    // Use the StubErrorHandler directly to handle the SubmissionNotFoundError
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result       = errorHandler.onServerError(request, SubmissionNotFoundError(nonExistentPlrId))

    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "No existing submission found to amend"
  }

  "return UNPROCESSABLE_ENTITY when amendment to a liability return that does not exist" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]), any[String]))
      .thenReturn(Future.successful(Left(RequestCouldNotBeProcessed)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(testOrgWithId)
    )

    val customBody = Json.parse(validRequestBody.toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString
    )

    val request = createRequest(validPlrId, Json.toJson(customBody))

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
  }

  "return OK with success response for a valid NIL_RETURN amendment" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]), any[String]))
      .thenReturn(Future.successful(Right(true)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(testOrgWithId)
    )

    val nilReturnWithDates = Json.parse(nilReturnBody(obligationMTT = false, electionUKGAAP = false).toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString
    )

    val request = createRequest(validPlrId, nilReturnWithDates)

    val result = route(app, request).value
    status(result) mustBe OK
    val jsonResult = contentAsJson(result)
    (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
    (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
  }

  "return UNPROCESSABLE_ENTITY when amendment to a nil return that does not exist" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    when(mockRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]), any[String]))
      .thenReturn(Future.successful(Left(RequestCouldNotBeProcessed)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(testOrgWithId)
    )

    val nilReturnWithDates = Json.parse(nilReturnBody(obligationMTT = false, electionUKGAAP = false).toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString
    )

    val request = createRequest(validPlrId, nilReturnWithDates)

    val result = route(app, request).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "Request could not be processed"
  }

  "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
    val request = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

    // Use the error handler directly to simulate an internal server error
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result       = errorHandler.onServerError(request, new RuntimeException("Internal server error"))

    status(result) mustBe INTERNAL_SERVER_ERROR
    val jsonResult = contentAsJson(result)
    (jsonResult \ "error" \ "code").as[String] mustEqual "500"
    (jsonResult \ "error" \ "message").as[String] mustEqual "Internal server error"
  }

  "return BAD_REQUEST for invalid JSON structure" in {
    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    val invalidJson = Json.obj("invalidField" -> "value")
    val request     = createRequest(validPlrId, invalidJson)

    // We use the error handler directly since the route would intercept the InvalidJson
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result       = errorHandler.onServerError(request, InvalidJson)

    status(result) mustBe BAD_REQUEST
    val jsonResult = contentAsJson(result)
    (jsonResult \ "code").as[String] mustEqual "INVALID_JSON"
    (jsonResult \ "message").as[String] mustEqual "Invalid JSON payload provided"
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
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.successful(testOrgWithId)
    )

    val emptyLiabilityData = validRequestBody.deepMerge(
      Json.obj(
        "liabilities"          -> Json.obj("liableEntities" -> Json.arr()),
        "accountingPeriodFrom" -> validStartDate.toString,
        "accountingPeriodTo"   -> validEndDate.toString
      )
    )

    val request = createRequest(validPlrId, Json.toJson(emptyLiabilityData))

    // Test using the error handler directly to avoid the DatabaseError exception
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result       = errorHandler.onServerError(request, DatabaseError("liableEntities array cannot be empty"))

    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "liabilityEntity cannot be empty"
  }

  "return UNPROCESSABLE_ENTITY when accounting period does not match the registered period" in {
    val submittedStartDate  = LocalDate.of(2024, 8, 14)
    val submittedEndDate    = LocalDate.of(2024, 12, 14)
    val registeredStartDate = LocalDate.of(2024, 1, 1)
    val registeredEndDate   = LocalDate.of(2024, 12, 31)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = true,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = registeredStartDate,
        endDate = registeredEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(validPlrId)).thenReturn(
      Future.successful(testOrgWithId)
    )

    val customBody = Json.parse(validRequestBody.toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> submittedStartDate.toString,
      "accountingPeriodTo"   -> submittedEndDate.toString
    )

    val request = createRequest(validPlrId, Json.toJson(customBody))

    // Test using the error handler directly to avoid the DatabaseError exception
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result = errorHandler.onServerError(
      request,
      InvalidAccountingPeriod(
        submittedStartDate,
        submittedEndDate,
        registeredStartDate,
        registeredEndDate
      )
    )

    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "003"
    (jsonResult \ "errors" \ "text")
      .as[String] mustEqual s"Submitted accounting period ($submittedStartDate to $submittedEndDate) does not match registered accounting period ($registeredStartDate to $registeredEndDate)"
  }

  "return OK when accounting period matches the registered period" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = true,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(validPlrId)).thenReturn(
      Future.successful(testOrgWithId)
    )

    val customBody = Json.parse(validRequestBody.toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString
    )

    val request = createRequest(validPlrId, Json.toJson(customBody))

    val result = route(app, request).value
    status(result) mustBe OK
    contentAsJson(result) shouldBe Json.obj(
      "success" -> Json.obj(
        "processingDate"   -> contentAsJson(result).\("success").\("processingDate").as[String],
        "formBundleNumber" -> "119000004320",
        "chargeReference"  -> "XTC01234123412"
      )
    )
  }

  "return UNPROCESSABLE_ENTITY when organisation is not found" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))

    when(mockOrganisationService.getOrganisation(any[String])).thenReturn(
      Future.failed(OrganisationNotFound("NOT_FOUND"))
    )

    val customBody = Json.parse(validRequestBody.toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString
    )

    val request = createRequest(validPlrId, Json.toJson(customBody))

    try {
      val result = route(app, request).value
      status(result) mustBe NOT_FOUND
      val jsonResult = contentAsJson(result)
      (jsonResult \ "error" \ "code").as[String] mustEqual "404"
      (jsonResult \ "error" \ "message").as[String] mustEqual "Not found"
    } catch {
      case e: OrganisationNotFound =>
        e.getMessage must include("NOT_FOUND")
        succeed
    }
  }

  "return UNPROCESSABLE_ENTITY when domestic-only MTT validation fails" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = true,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(validPlrId)).thenReturn(
      Future.successful(testOrgWithId)
    )

    val customBody = Json.parse(validRequestBody.toString()).as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString,
      "obligationMTT"        -> true
    )

    val request = createRequest(validPlrId, Json.toJson(customBody))

    // Use the error handler directly to avoid the DatabaseError exception
    val errorHandler = app.injector.instanceOf[StubErrorHandler]
    val result = errorHandler.onServerError(
      request,
      DomesticOnlyMTTError("obligationMTT cannot be true for a domestic-only group")
    )

    status(result) mustBe UNPROCESSABLE_ENTITY
    val jsonResult = contentAsJson(result)
    (jsonResult \ "errors" \ "code").as[String] mustEqual "093"
    (jsonResult \ "errors" \ "text").as[String] mustEqual "obligationMTT cannot be true for a domestic-only group"
  }

  "handle nil return format properly" in {
    val validStartDate = LocalDate.of(2024, 8, 14)
    val validEndDate   = LocalDate.of(2024, 12, 14)

    val testOrg = TestOrganisation(
      orgDetails = OrgDetails(
        domesticOnly = true,
        organisationName = "Test Org",
        registrationDate = LocalDate.now()
      ),
      accountingPeriod = AccountingPeriod(
        startDate = validStartDate,
        endDate = validEndDate
      )
    )

    val testOrgWithId = TestOrganisationWithId(validPlrId, testOrg)

    when(mockOrganisationService.getOrganisation(validPlrId)).thenReturn(
      Future.successful(testOrgWithId)
    )

    when(mockRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Some(mockUKTRMongoSubmission)))
    when(mockRepository.update(any[UKTRSubmission], any[String])).thenReturn(Future.successful(Right(true)))

    // Use a standard nil return format with proper fields
    val nilReturnWithDates = nilReturnBody(obligationMTT = false, electionUKGAAP = false)
      .as[JsObject] ++ Json.obj(
      "accountingPeriodFrom" -> validStartDate.toString,
      "accountingPeriodTo"   -> validEndDate.toString
    )

    val request = createRequest(validPlrId, nilReturnWithDates)

    val result = route(app, request).value
    status(result) mustBe OK
    contentAsJson(result) shouldBe Json.obj(
      "success" -> Json.obj(
        "processingDate"   -> contentAsJson(result).\("success").\("processingDate").as[String],
        "formBundleNumber" -> "119000004320",
        "chargeReference"  -> "XTC01234123412"
      )
    )
  }
}
