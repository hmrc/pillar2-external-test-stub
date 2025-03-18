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

import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.OptionValues
import org.scalatest.compatible.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.ObligationsAndSubmissionsRepository
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture with MockitoSugar {

  implicit class AwaitFuture(fut: Future[Result]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err mustEqual expected
    }
  }

  private val mockUKTRRepository = mock[UKTRSubmissionRepository]
  private val mockOasRepository  = mock[ObligationsAndSubmissionsRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[UKTRSubmissionRepository].toInstance(mockUKTRRepository),
        inject.bind[ObligationsAndSubmissionsRepository].toInstance(mockOasRepository),
        inject.bind[OrganisationService].toInstance(mockOrgService)
      )
      .build()

  private def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest("POST", routes.SubmitUKTRController.submitUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  "UK Tax Return Submission" - {
    "when submitting a UK tax return" - {
      "should return CREATED with success response for a valid liability submission" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.insert(any[UKTRSubmission](), eqTo(validPlrId), eqTo(false))).thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[UKTRSubmission](), eqTo(validPlrId), any[ObjectId])).thenReturn(Future.successful(true))

        val result = route(app, createRequest(validPlrId, validRequestBody)).get
        status(result) mustBe CREATED
        contentAsJson(result) mustEqual Json.toJson(LiabilityReturnSuccess.successfulUKTRResponse)
      }

      "should return CREATED with success response for a valid NIL return submission" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.insert(any[UKTRSubmission](), eqTo(validPlrId), eqTo(false))).thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[UKTRSubmission](), eqTo(validPlrId), any[ObjectId])).thenReturn(Future.successful(true))

        val result = route(app, createRequest(validPlrId, nilReturnBody(obligationMTT = false, electionUKGAAP = false))).get
        status(result) mustBe CREATED
        contentAsJson(result) mustEqual Json.toJson(NilReturnSuccess.successfulNilReturnResponse)
      }

      "should return ETMPBadRequest when the request body is invalid JSON" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val invalidJsonBody = Json.obj(
          "someField" -> "someValue"
        )
        val request = createRequest(validPlrId, invalidJsonBody)

        route(app, request).value shouldFailWith ETMPBadRequest
      }

      "should return Pillar2IdMissing when X-Pillar2-Id header is missing" in {
        val request = FakeRequest("POST", routes.SubmitUKTRController.submitUKTR.url)
          .withHeaders(authHeader)
          .withBody(validRequestBody)

        route(app, request).value shouldFailWith Pillar2IdMissing
      }

      "should return NoActiveSubscription when organisation not found" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

        val request = createRequest(validPlrId, validRequestBody)

        route(app, request).value shouldFailWith NoActiveSubscription
      }

      "should return InvalidReturn when accounting period doesn't match" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val invalidAccountingPeriodBody = validRequestBody.deepMerge(
          Json.obj(
            "accountingPeriodFrom" -> "2023-01-01",
            "accountingPeriodTo"   -> "2023-12-31"
          )
        )
        val request = createRequest(validPlrId, invalidAccountingPeriodBody)

        route(app, request).value shouldFailWith InvalidReturn
      }

      "should return InvalidReturn when liableEntities array is empty" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val emptyLiableEntitiesBody = validRequestBody.deepMerge(
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
        val request = createRequest(validPlrId, emptyLiableEntitiesBody)

        route(app, request).value shouldFailWith InvalidReturn
      }

      "should return ETMPInternalServerError for specific Pillar2Id" in {
        val request = createRequest(ServerErrorPlrId, validRequestBody)

        route(app, request).value shouldFailWith ETMPInternalServerError
      }

      "should return FORBIDDEN when missing Authorization header" in {
        val request = FakeRequest("POST", routes.SubmitUKTRController.submitUKTR.url)
          .withHeaders("X-Pillar2-Id" -> validPlrId)
          .withBody(validRequestBody)

        val result = route(app, request).get
        status(result) mustBe FORBIDDEN
      }

      "should return InvalidTotalLiability when submitting with invalid amounts" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

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

      "should return InvalidTotalLiability when total liability does not match sum of components" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val mismatchedTotalBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "totalLiability" -> 50000.00 // Doesn't match sum of DTT + IIR + UTPR
            )
          )
        )
        val request = createRequest(validPlrId, mismatchedTotalBody)

        route(app, request).value shouldFailWith InvalidTotalLiability
      }

      "should return InvalidTotalLiability when any component is invalid" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val invalidComponentBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "totalLiabilityDTT"  -> 5000.99,
              "totalLiabilityIIR"  -> -100.00,
              "totalLiabilityUTPR" -> 10000.99,
              "totalLiability"     -> 15901.98 // Sum would be correct if IIR wasn't negative
            )
          )
        )
        val request = createRequest(validPlrId, invalidComponentBody)

        route(app, request).value shouldFailWith InvalidTotalLiability
      }

      "should return ETMPBadRequest when UKTRSubmission is neither a UKTRNilReturn nor a UKTRLiabilityReturn" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val customSubmissionBody = Json.obj(
          "accountingPeriodFrom" -> "2024-01-01",
          "accountingPeriodTo"   -> "2024-12-31",
          "obligationMTT"        -> false,
          "electionUKGAAP"       -> false,
          "liabilities" -> Json.obj(
            "customType" -> "NEITHER_NIL_NOR_LIABILITY"
          )
        )

        val request = createRequest(validPlrId, customSubmissionBody)

        route(app, request).value shouldFailWith ETMPBadRequest
      }

      "should return InvalidReturn when submitting with invalid ID type" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        val invalidIdTypeBody = validRequestBody.deepMerge(
          Json.obj(
            "liabilities" -> Json.obj(
              "liableEntities" -> Json.arr(
                validLiableEntity.as[JsObject] ++ Json.obj("idType" -> "INVALID")
              )
            )
          )
        )
        val request = createRequest(validPlrId, invalidIdTypeBody)

        route(app, request).value shouldFailWith InvalidReturn
      }

      "should return DatabaseError when database insert fails" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockUKTRRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
        when(
          mockUKTRRepository.insert(
            argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRSubmission]),
            anyString(),
            eqTo(false)
          )
        ).thenReturn(Future.successful(new ObjectId()))
        when(
          mockOasRepository.insert(
            argThat((submission: UKTRSubmission) => submission.isInstanceOf[UKTRSubmission]),
            anyString(),
            any[ObjectId]
          )
        ).thenReturn(Future.failed(DatabaseError("Failed to insert submission into mongo")))

        val request = createRequest(validPlrId, Json.toJson(validRequestBody))

        route(app, request).value shouldFailWith DatabaseError("Failed to insert submission into mongo")
      }
    }
  }
}
