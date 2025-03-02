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

import org.mockito.ArgumentMatchers.{any => mockAny}
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.{BAD_REQUEST, CREATED, UNPROCESSABLE_ENTITY}
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.{DatabaseError, OrganisationNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate
import scala.concurrent.Future

class SubmitUKTRControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with UKTRDataFixture
    with MockitoSugar
    with BeforeAndAfterEach {

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

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[UKTRSubmissionRepository].toInstance(mockRepository),
        inject.bind[OrganisationService].toInstance(mockOrganisationService)
      )
      .build()

  override def beforeEach(): Unit = {
    reset(mockRepository)
    reset(mockOrganisationService)
    setupDefaultMockBehavior()
  }

  private def setupDefaultMockBehavior(): Unit = {
    when(mockRepository.insert(mockAny[UKTRSubmission], mockAny[String], mockAny[Boolean])).thenReturn(Future.successful(true))
    when(mockRepository.isDuplicateSubmission(mockAny[String], mockAny[LocalDate], mockAny[LocalDate])).thenReturn(Future.successful(false))
    when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
      Future.successful(createTestOrganisationWithId("XMPLR0123456789", "2024-08-14", "2024-12-14"))
    )
    ()
  }

  private def setupDuplicateSubmissionBehavior(): Unit = {
    when(mockRepository.isDuplicateSubmission(mockAny[String], mockAny[LocalDate], mockAny[LocalDate])).thenReturn(Future.successful(true))
    ()
  }

  def request(plrReference: String = validPlrId, body: JsObject): FakeRequest[JsObject] =
    FakeRequest(POST, routes.SubmitUKTRController.submitUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrReference)
      .withBody(body)

  "when subscription cannot be fetched" - {
    "422 response should be returned" in {
      val result = route(app, request(plrReference = "XEPLR0123456500", body = validRequestBody)).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      (contentAsJson(result) \ "errors" \ "code").as[String] mustEqual "007"
    }
  }

  "when pillar2Id is missing" - {
    "a 422 should be returned" in {
      val missingPlrIdRequest = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR.url)
        .withHeaders("Content-Type" -> "application/json", authHeader)
        .withBody(validRequestBody)
      val result = route(app, missingPlrIdRequest).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      (contentAsJson(result) \ "errors" \ "code").as[String] mustEqual "002"
    }
  }

  "when invalid JSON is submitted" - {
    "a 400 should be returned" in {
      val result = route(app, request(body = Json.obj("invalid" -> true))).value
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error" \ "code").as[String] mustEqual "400"
    }
  }

  "when submitting a Liability UKTR" - {
    "should return CREATED (201)" - {
      "when plrReference is valid and JSON payload is correct" in {
        val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )

        val result = route(app, request(body = validRequestBody)).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XY123456789012"
      }

      "when plrReference is valid and JSON is correct and has 3 Liable Entities" in {
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
    }

    "should return UNPROCESSABLE_ENTITY (422)" - {
      "when totalLiability is invalid" in {
        val invalidAmounts = List(
          BigDecimal("-1.00"),
          BigDecimal("10000000000000.00"),
          BigDecimal("100.999")
        )

        invalidAmounts.foreach { amount =>
          val result = route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiability" -> amount))))).value
          status(result) mustBe UNPROCESSABLE_ENTITY

          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_096
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "totalLiability must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityDTT is invalid" in {
        val invalidAmounts = List(
          BigDecimal("-1.00"),
          BigDecimal("10000000000000.00"),
          BigDecimal("100.999")
        )

        invalidAmounts.foreach { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityDTT" -> amount))))).value
          status(result) mustBe UNPROCESSABLE_ENTITY

          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_DTT_098
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityDTT must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityIIR is invalid" in {
        val invalidAmounts = List(
          BigDecimal("-1.00"),
          BigDecimal("10000000000000.00"),
          BigDecimal("100.999")
        )

        invalidAmounts.foreach { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityIIR" -> amount))))).value
          status(result) mustBe UNPROCESSABLE_ENTITY

          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_IIR_097
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityUTPR is invalid" in {
        val invalidAmounts = List(
          BigDecimal("-1.00"),
          BigDecimal("10000000000000.00"),
          BigDecimal("100.999")
        )

        invalidAmounts.foreach { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityUTPR" -> amount))))).value
          status(result) mustBe UNPROCESSABLE_ENTITY

          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_UTPR_099
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityUTPR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when liabilityEntity is invalid" in {
        val result = route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr()))))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
        (json \ "errors" \ "text").as[String] mustBe "liabilityEntity cannot be empty"
      }

      "when ukChargeableEntityName is Invalid" in {
        val result = route(app, request(body = invalidUkChargeableEntityNameRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when ukChargeableEntityName exceeds 160 characters" in {
        val result = route(app, request(body = ukChargeableEntityNameTooLongRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when ukChargeableEntityName is Empty in 3rd LiableEntity" in {
        val result = route(
          app,
          request(body =
            validRequestBody.deepMerge(
              Json.obj(
                "liabilities" -> Json.obj(
                  "liableEntities" -> Json.arr(validLiableEntity, validLiableEntity, validLiableEntity ++ Json.obj("ukChargeableEntityName" -> ""))
                )
              )
            )
          )
        ).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when idType has zero length" in {
        val result = route(app, request(body = invalidIdTypeZeroLengthRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idType is Invalid" in {
        val result = route(app, request(body = invalidIdTypeRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idType in LiableEntity1 is Invalid and idValue in LiableEntity2 is Invalid" in {
        val result = route(app, request(body = invalidIdTypeEntity1AndInvalidIdValueEntity2RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idValue has zero length" in {
        val result = route(app, request(body = invalidIdValueZeroLengthRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
      }

      "when idValue length exceeds 15 characters" in {
        val result = route(app, request(body = invalidIdValueLengthExceeds15RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
      }

      "when amountOwedDTT is Invalid" in {
        invalidUKTRAmounts.foreach { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedDTT" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "amountOwedDTT must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedIIR is Invalid" in {
        invalidUKTRAmounts.foreach { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedIIR" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "amountOwedIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedUTPR is Invalid" in {
        invalidUKTRAmounts.foreach { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedUTPR" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val responseJson = contentAsJson(result)
          (responseJson \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (responseJson \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (responseJson \ "errors" \ "text")
            .as[String] mustBe "amountOwedUTPR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedIIR is Invalid in LiableEntity2 and amountOwedUTPR is Invalid in LiableEntity3" in {
        val result = route(app, request(body = invalidAmountOwedIIREntity2AndInvalidAmountOwedUTPREntity3RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text")
          .as[String] mustBe "amountOwedIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
      }

      "when accounting period does not match the registered period" in {
        val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-01-01", "2024-12-31"))
        )

        val result = route(app, request(body = validRequestBody)).value
        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text")
          .as[String] mustBe "Accounting period (2024-08-14 to 2024-12-14) does not match the registered period (2024-01-01 to 2024-12-31)"
      }

      "when organisation service fails" in {
        val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(Future.failed(new RuntimeException("Test error")))

        val result = route(app, request(body = validRequestBody)).value
        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "Request could not be processed"
      }

      "when a general unexpected exception occurs" in {
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )
        when(mockRepository.isDuplicateSubmission(mockAny[String], mockAny[LocalDate], mockAny[LocalDate]))
          .thenReturn(Future.successful(false))
        when(mockRepository.insert(mockAny[UKTRSubmission], mockAny[String], mockAny[Boolean]))
          .thenReturn(Future.failed(new RuntimeException("Unknown error")))

        val result = route(app, request(body = validRequestBody)).value

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "500",
            "message" -> "Internal server error",
            "logID"   -> "C0000000000000000000000000000500"
          )
        )
      }
    }

    "should return BAD_REQUEST (400)" - {
      "when ukChargeableEntityName is missing" in {
        val result = route(app, request(body = missingUkChargeableEntityNameRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when ukChargeableEntityName is missing and invalidLiableEntityukChargeableEntityNameZeroLength" in {
        val result = route(app, request(body = missingUkChargeableEntNameLiableEntity2AndInvalidIdTypeLiableEnt3ReqBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when idType is missing" in {
        val result = route(app, request(body = missingIdTypeRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when idValue is missing" in {
        val result = route(app, request(body = missingIdValueRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedDTT is missing" in {
        val result = route(app, request(body = missingAmountOwedDTTRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedIIR is missing" in {
        val result = route(app, request(body = missingAmountOwedIIRRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when amountOwedUTPR is missing" in {
        val result = route(app, request(body = missingAmountOwedUTPRRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
    }

    "should return INTERNAL_SERVER_ERROR (500)" - {
      "when plrReference indicates SAP failure" in {
        val result = route(app, request(plrReference = ServerErrorPlrId, body = validRequestBody)).value

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "500",
            "message" -> "Internal server error",
            "logID"   -> "C0000000000000000000000000000500"
          )
        )
      }
    }
  }

  "when submitting a nil UKTR" - {
    "should return CREATED (201)" - {
      "when submitting a Domestic-Only Nil Return with electionUKGAAP = true" in {
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value
        status(result) mustBe CREATED
      }

      "when submitting a Domestic-Only Nil Return with electionUKGAAP = false" in {
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value
        status(result) mustBe CREATED
      }

      "when submitting a Non-Domestic Nil Return with electionUKGAAP = false" in {

        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(
            createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14").copy(
              organisation = createTestOrganisation("2024-08-14", "2024-12-14").copy(
                orgDetails = OrgDetails(
                  domesticOnly = false,
                  organisationName = "Test Org",
                  registrationDate = LocalDate.now()
                )
              )
            )
          )
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value

        status(result) mustBe CREATED
      }

      "when submitting a domestic-only Nil Return with obligationMTT = false" in {
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value
        status(result) mustBe CREATED
      }

      "when submitting a non-domestic Nil Return with obligationMTT = false" in {

        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(
            createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14").copy(
              organisation = createTestOrganisation("2024-08-14", "2024-12-14").copy(
                orgDetails = OrgDetails(
                  domesticOnly = false,
                  organisationName = "Test Org",
                  registrationDate = LocalDate.now()
                )
              )
            )
          )
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value

        status(result) mustBe CREATED
      }

      "when submitting a non-domestic Nil Return with obligationMTT = true" in {

        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(
            createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14").copy(
              organisation = createTestOrganisation("2024-08-14", "2024-12-14").copy(
                orgDetails = OrgDetails(
                  domesticOnly = false,
                  organisationName = "Test Org",
                  registrationDate = LocalDate.now()
                )
              )
            )
          )
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = true, electionUKGAAP = false))).value

        status(result) mustBe CREATED
      }
    }

    "should return UNPROCESSABLE_ENTITY (422)" - {
      "when submitting a domestic-only Nil Return with obligationMTT = true" in {
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = true, electionUKGAAP = false))).value
        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
        (json \ "errors" \ "text").as[String] mustBe "obligationMTT cannot be true for a domestic-only group"
      }

      "when submitting a Non-Domestic Nil Return with electionUKGAAP = true" in {

        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(
            createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14").copy(
              organisation = createTestOrganisation("2024-08-14", "2024-12-14").copy(
                orgDetails = OrgDetails(
                  domesticOnly = false,
                  organisationName = "Test Org",
                  registrationDate = LocalDate.now()
                )
              )
            )
          )
        )
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
        (json \ "errors" \ "text").as[String] mustBe "electionUKGAAP can be true only for a domestic-only group"
      }
    }

    "should return BAD_REQUEST (400)" - {
      "when NilReturn AccountingPeriodFrom date is invalid" in {
        val result = route(app, request(body = invalidAccountingPeriodFromNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodFrom date is missing" in {
        val result = route(app, request(body = missingAccountingPeriodFromNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodTo date is invalid" in {
        val result = route(app, request(body = invalidAccountingPeriodToNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodTo date is missing" in {
        val result = route(app, request(body = missingAccountingPeriodToNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ObligationMTT field is invalid" in {
        val result = route(app, request(body = invalidObligationMTTNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ObligationMTT field is missing" in {
        val result = route(app, request(body = missingObligationMTTNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ElectionUKGAAP field is invalid" in {
        val result = route(app, request(body = invalidElectionUKGAAPNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ElectionUKGAAP field is missing" in {
        val result = route(app, request(body = missingElectionUKGAAPNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn returnType is invalid" in {
        val result = route(app, request(body = invalidReturnTypeNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn returnType is empty" in {
        val result = route(app, request(body = emptyReturnTypeNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
    }
  }

  "when submitting a duplicate UKTR" - {
    "should return 422 with error code 044 for duplicate liability return" in {

      val result1 = route(app, request(body = validRequestBody)).value
      status(result1) mustBe CREATED

      setupDuplicateSubmissionBehavior()

      val result2 = route(app, request(body = validRequestBody)).value
      status(result2) mustBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result2)
      (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.DUPLICATE_SUBMISSION_044
      (json \ "errors" \ "text").as[String] mustBe "A submission already exists for this accounting period"
    }

    "should return 422 with error code 044 for duplicate nil return" in {

      val result1 = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value
      status(result1) mustBe CREATED

      setupDuplicateSubmissionBehavior()

      val result2 = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value
      status(result2) mustBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result2)
      (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.DUPLICATE_SUBMISSION_044
      (json \ "errors" \ "text").as[String] mustBe "A submission already exists for this accounting period"
    }
  }

  "Boundary testing" - {
    "should handle boundary cases for amounts" in {
      val boundaryAmounts = List(
        (BigDecimal(0), CREATED),
        (BigDecimal("9999999999999.99"), CREATED),
        (BigDecimal("10000000000000.00"), UNPROCESSABLE_ENTITY),
        (BigDecimal(-0.01), UNPROCESSABLE_ENTITY),
        (BigDecimal("100.999"), UNPROCESSABLE_ENTITY)
      )

      boundaryAmounts.foreach { case (amount, expectedStatus) =>
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )

        val modifiedBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj("totalLiability" -> amount)
          )
        )

        val result = route(app, request(body = modifiedBody)).value
        status(result) mustBe expectedStatus
      }
    }

    "should handle boundary cases for string fields" in {
      val boundaryStrings = List(
        ("", UNPROCESSABLE_ENTITY),
        ("a" * 160, CREATED),
        ("a" * 161, UNPROCESSABLE_ENTITY),
        ("Test & Company's Ltd-", CREATED),
        ("Test @ Company", UNPROCESSABLE_ENTITY)
      )

      boundaryStrings.foreach { case (value, expectedStatus) =>
        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
        )

        val modifiedBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "liableEntities" -> Json.arr(
                validLiableEntity.deepMerge(Json.obj("ukChargeableEntityName" -> value))
              )
            )
          )
        )

        val result = route(app, request(body = modifiedBody)).value
        status(result) mustBe expectedStatus
      }
    }

    "should handle boundary cases for accounting periods" in {
      val boundaryDates = List(
        ("2024-01-01", "2024-12-31", CREATED),
        ("2024-01-01", "2024-01-01", CREATED),
        ("2024-12-31", "2024-01-01", BAD_REQUEST),
        ("2024-01-99", "2024-12-31", BAD_REQUEST),
        ("2024-13-01", "2024-12-31", BAD_REQUEST)
      )

      boundaryDates.foreach { case (startDate, endDate, expectedStatus) =>
        val modifiedBody = validRequestBody.deepMerge(
          Json.obj(
            "accountingPeriodFrom" -> startDate,
            "accountingPeriodTo"   -> endDate
          )
        )

        val (orgStartDate, orgEndDate) = expectedStatus match {
          case CREATED => (startDate, endDate)
          case _       => ("2024-01-01", "2024-12-31")
        }

        when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
          Future.successful(createTestOrganisationWithId(validPlrId, orgStartDate, orgEndDate))
        )

        val result = route(app, request(body = modifiedBody)).value
        status(result) mustBe expectedStatus
      }
    }
  }

  "when a duplicate submission is detected" in {
    setupDuplicateSubmissionBehavior()

    val result = route(app, request(body = validRequestBody)).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val json = contentAsJson(result)
    (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.DUPLICATE_SUBMISSION_044
    (json \ "errors" \ "text").as[String] mustBe "A submission already exists for this accounting period"
  }

  val domesticOnlyWithMTTRequestBody: JsObject = Json.obj(
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
      "liableEntities"           -> Json.arr(validLiableEntity)
    )
  )

  "when a domestic-only group tries to submit with obligationMTT=true" in {
    val result = route(app, request(body = domesticOnlyWithMTTRequestBody)).value
    status(result) mustBe UNPROCESSABLE_ENTITY
    val json = contentAsJson(result)
    (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
    (json \ "errors" \ "text").as[String] mustBe "obligationMTT cannot be true for a domestic-only group"
  }

  "Error handling during repository operations" - {
    "should return 422 when findByPillar2Id fails with DatabaseError" in {
      val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
      )
      when(mockRepository.isDuplicateSubmission(mockAny[String], mockAny[LocalDate], mockAny[LocalDate])).thenReturn(
        Future.failed(DatabaseError("Database error during isDuplicateSubmission"))
      )

      val result = route(app, request(body = validRequestBody)).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
    }

    "should return 422 when insert fails with DatabaseError" in {
      val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
      )
      when(mockRepository.insert(mockAny[UKTRSubmission], mockAny[String], mockAny[Boolean])).thenReturn(
        Future.failed(DatabaseError("Database error during insert"))
      )

      val result = route(app, request(body = validRequestBody)).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
    }
  }

  "Error handling during organisation retrieval" - {
    "should return 500 when getOrganisation fails with OrganisationNotFound" in {
      val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
        Future.failed(OrganisationNotFound(validPlrId))
      )

      val result = route(app, request(body = validRequestBody)).value
      status(result) mustBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "code").as[String] mustBe "500"
    }

    "should return 400 when nil return validation fails with appropriate error" in {
      val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
      )

      // Create a nil return with an invalid field to trigger validation error
      val invalidNilReturnBody = Json.obj(
        "accountingPeriodFrom" -> "2024-08-14",
        "accountingPeriodTo"   -> "2024-12-14",
        "obligationMTT"        -> "not-a-boolean", // Invalid boolean value
        "electionUKGAAP"       -> true,
        "returnType"           -> "Nil"
      )

      val result = route(app, request(body = invalidNilReturnBody)).value
      status(result) mustBe BAD_REQUEST
    }
  }

  "Generic exception handling" - {
    "should return 500 for unexpected exceptions during nil return processing" in {
      val _ = when(mockOrganisationService.getOrganisation(mockAny[String])).thenReturn(
        Future.successful(createTestOrganisationWithId(validPlrId, "2024-08-14", "2024-12-14"))
      )
      when(mockRepository.isDuplicateSubmission(mockAny[String], mockAny[LocalDate], mockAny[LocalDate])).thenReturn(
        Future.failed(new RuntimeException("Unexpected error"))
      )

      val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value
      status(result) mustBe INTERNAL_SERVER_ERROR

      // Check that we have a valid JSON response with error code 500
      contentAsJson(result).as[JsObject].keys must contain("error")
      (contentAsJson(result) \ "error" \ "code").as[String] mustBe "500"
    }
  }
}
