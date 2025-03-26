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

import org.mockito.ArgumentMatchers.{eq => eqTo, _}
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.repositories.{BTNSubmissionRepository, ObligationsAndSubmissionsRepository}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate
import scala.concurrent.Future

class BTNControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with MockitoSugar with BTNDataFixture with TestOrgDataFixture {

  private val mockBTNRepository = mock[BTNSubmissionRepository]
  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .overrides(inject.bind[BTNSubmissionRepository].toInstance(mockBTNRepository))
      .overrides(inject.bind[ObligationsAndSubmissionsRepository].toInstance(mockOasRepository))
      .build()

  "Below Threshold Notification" - {
    "when submitting a notification" - {
      "should return CREATED with success response for a valid submission" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
        when(mockBTNRepository.findByPillar2Id(eqTo(validPlrId))).thenReturn(Future.successful(Seq.empty))
        when(mockBTNRepository.insert(eqTo(validPlrId), any[BTNRequest])).thenReturn(Future.successful(new ObjectId()))
        when(
          mockOasRepository.insert(
            argThat((submission: BaseSubmission) => submission.isInstanceOf[BTNRequest]),
            anyString(),
            any[ObjectId]
          )
        ).thenReturn(Future.successful(true))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
        status(result) shouldBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined shouldBe true
      }

      "should return Pillar2IdMissing when X-Pillar2-Id header is missing" in {
        val request = FakeRequest(POST, "/RESTAdapter/plr/below-threshold-notification")
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(validBTNRequestBody)

        val result = route(app, request).get
        result shouldFailWith Pillar2IdMissing
      }

      "should return Pillar2IdMissing when Pillar2 ID format is invalid" in {
        val invalidPlrId = "invalid@id"
        val result       = route(app, createRequestWithBody(invalidPlrId, validBTNRequest)).get
        result shouldFailWith Pillar2IdMissing
      }

      "should return NoActiveSubscription when organisation not found" in {
        when(mockOrgService.getOrganisation(any[String]))
          .thenReturn(Future.failed(OrganisationNotFound("Organisation not found")))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
        result shouldFailWith NoActiveSubscription
      }

      "should return RequestCouldNotBeProcessed when the accounting period doesn't match organisation's" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

        val mismatchedRequest = validBTNRequest.copy(
          accountingPeriodFrom = LocalDate.of(2024, 2, 1),
          accountingPeriodTo = LocalDate.of(2024, 12, 31)
        )

        when(mockOrgService.getOrganisation(validPlrId))
          .thenReturn(Future.successful(organisationWithId))

        val result = route(app, createRequestWithBody(validPlrId, mismatchedRequest)).get
        result shouldFailWith RequestCouldNotBeProcessed
      }

      "should return DuplicateSubmissionError when duplicate submission exists" in {
        when(mockOrgService.getOrganisation(validPlrId)).thenReturn(Future.successful(organisationWithId))
        when(mockBTNRepository.findByPillar2Id(any[String])).thenReturn(Future.successful(Seq(BTNMongoSubmission)))

        val result = route(app, createRequestWithBody(validPlrId, validBTNRequest)).get
        result shouldFailWith DuplicateSubmission
      }

      "should return RequestCouldNotBeProcessed when accounting period is invalid" in {
        val invalidBTNRequest = validBTNRequest.copy(
          accountingPeriodFrom = LocalDate.of(2024, 12, 31),
          accountingPeriodTo = LocalDate.of(2024, 1, 1)
        )

        val result = route(app, createRequestWithBody(validPlrId, invalidBTNRequest)).get
        result shouldFailWith RequestCouldNotBeProcessed
      }

      "should return ETMPBadRequest when request body is invalid JSON" in {
        val result = route(app, createRequest(validPlrId, Json.obj("invalid" -> "request"))).get
        result shouldFailWith ETMPBadRequest
      }

      "should return ETMPInternalServerError when specific Pillar2 ID indicates server error" in {
        val result = route(app, createRequestWithBody(serverErrorPlrId, validBTNRequest)).get
        result shouldFailWith ETMPInternalServerError
      }
    }
  }
}
