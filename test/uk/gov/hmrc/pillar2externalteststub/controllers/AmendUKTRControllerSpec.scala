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
import org.mongodb.scala.bson.ObjectId
import org.scalatest.OptionValues
import org.scalatest.compatible.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
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
import uk.gov.hmrc.pillar2externalteststub.models.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.{DatabaseError, OrganisationNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn, UKTRSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.{ObligationsAndSubmissionsRepository, UKTRSubmissionRepository}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class AmendUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture with MockitoSugar {

  implicit class AwaitFuture(fut: Future[Result]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err mustEqual expected
    }
  }

  private val mockUKTRRepository = mock[UKTRSubmissionRepository]
  private val mockOrgService     = mock[OrganisationService]
  private val mockOasRepository  = mock[ObligationsAndSubmissionsRepository]

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[UKTRSubmissionRepository].toInstance(mockUKTRRepository),
        inject.bind[ObligationsAndSubmissionsRepository].toInstance(mockOasRepository),
        inject.bind[OrganisationService].toInstance(mockOrgService)
      )
      .build()

  "UK Tax Return Amendment" - {
    "when amending a UK tax return" - {
      "should return OK with success response for a valid liability amendment" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
        when(
          mockOasRepository.insert(
            argThat((submission: BaseSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
            anyString(),
            any[ObjectId]
          )
        ).thenReturn(Future.successful(true))
        when(
          mockUKTRRepository.update(
            argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
            any[String]
          )
        ).thenReturn(Future.successful(Right(new ObjectId())))

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))

        val result = route(app, request).value
        status(result) mustBe OK
        val jsonResult = contentAsJson(result)
        (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
        (jsonResult \ "success" \ "chargeReference").as[String] mustEqual "XTC01234123412"
        (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
      }

      "should return OK with success response for a valid NIL_RETURN amendment" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
        when(mockUKTRRepository.update(argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]), any[String]))
          .thenReturn(Future.successful(Right(new ObjectId())))
        when(
          mockOasRepository.insert(
            argThat((submission: BaseSubmission) => submission.isInstanceOf[UKTRNilReturn]),
            anyString(),
            any[ObjectId]
          )
        ).thenReturn(Future.successful(true))

        val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

        val result = route(app, request).value
        status(result) mustBe OK
        val jsonResult = contentAsJson(result)
        (jsonResult \ "success" \ "formBundleNumber").as[String] mustEqual "119000004320"
        (jsonResult \ "success" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
      }

      "should return Pillar2IdMissing when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith Pillar2IdMissing
      }

      "should return NoActiveSubscription when organisation not found" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith NoActiveSubscription
      }

      "should return RequestCouldNotBeProcessed when previous submission does not exist" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(None))

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should return RequestCouldNotBeProcessed when amendment to a liability return fails" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
        when(
          mockUKTRRepository.update(
            argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
            any[String]
          )
        ).thenReturn(Future.failed(RequestCouldNotBeProcessed))

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should return RequestCouldNotBeProcessed when amendment to a nil return fails" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
        when(
          mockUKTRRepository.update(
            argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRNilReturn]),
            any[String]
          )
        ).thenReturn(Future.failed(RequestCouldNotBeProcessed))

        val request = createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))

        route(app, request).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should return ETMPInternalServerError for specific Pillar2Id" in {
        val request = createRequest(ServerErrorPlrId, Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith ETMPInternalServerError
      }

      "should return ETMPBadRequest for invalid JSON structure" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val invalidJson = Json.obj("invalidField" -> "value", "anotherInvalidField" -> 123)
        val request     = createRequest(validPlrId, invalidJson)

        route(app, request).value shouldFailWith ETMPBadRequest
      }

      "should return InvalidReturn if liableEntities array is empty" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val emptyLiabilityData = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "liableEntities"     -> Json.arr(),
              "totalLiability"     -> 0,
              "totalLiabilityDTT"  -> 0,
              "totalLiabilityIIR"  -> 0,
              "totalLiabilityUTPR" -> 0
            )
          )
        )

        val request = createRequest(validPlrId, Json.toJson(emptyLiabilityData))

        route(app, request).value shouldFailWith InvalidReturn
      }

      "should return InvalidTotalLiability when amending with invalid amounts" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val invalidAmountsBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "totalLiability"    -> -500,
              "totalLiabilityDTT" -> 10000000000000.99
            )
          )
        )

        route(app, createRequest(validPlrId, invalidAmountsBody)).value shouldFailWith InvalidTotalLiability
      }

      "should return InvalidTotalLiability when total liability does not match sum of components in amendment" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val mismatchedTotalBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "totalLiability" -> 50000.00 // Doesn't match sum of DTT + IIR + UTPR
            )
          )
        )

        route(app, createRequest(validPlrId, mismatchedTotalBody)).value shouldFailWith InvalidTotalLiability
      }

      "should return InvalidReturn when amending with invalid ID type" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

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

      "should return FORBIDDEN when missing Authorization header" in {
        val requestWithoutAuth = FakeRequest(PUT, routes.AmendUKTRController.amendUKTR.url)
          .withHeaders("Content-Type" -> "application/json", "X-Pillar2-Id" -> validPlrId)
          .withBody(Json.toJson(validRequestBody))

        val result = route(app, requestWithoutAuth).value
        status(result) mustBe FORBIDDEN
      }

      "should return ETMPBadRequest when required fields are missing" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val missingRequiredFields = Json.obj(
          "accountingPeriodFrom" -> "2024-08-14",
          "obligationMTT"        -> false,
          "electionUKGAAP"       -> false
        )

        route(app, createRequest(validPlrId, missingRequiredFields)).value shouldFailWith ETMPBadRequest
      }

      "should return DatabaseError when database insert fails" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
        when(
          mockUKTRRepository.update(
            argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
            any[String]
          )
        ).thenReturn(Future.successful(Right(new ObjectId())))
        when(
          mockOasRepository.insert(
            argThat((submission: BaseSubmission) => submission.isInstanceOf[UKTRLiabilityReturn]),
            anyString(),
            any[ObjectId]
          )
        ).thenReturn(Future.failed(DatabaseError(s"Failed to save entry to submission history collection: test error")))

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith DatabaseError(s"Failed to save entry to submission history collection: test error")
      }
    }
  }
}
