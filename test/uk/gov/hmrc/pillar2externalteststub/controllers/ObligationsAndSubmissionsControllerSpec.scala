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

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.mongodb.scala.bson.ObjectId
import org.scalatest.OptionValues
import org.scalatest.compatible.Assertion
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.{TestOrgDataFixture, UKTRDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{ETMPInternalServerError, NoAssociatedDataFound, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.ObligationsAndSubmissionsMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.ObligationsAndSubmissionsRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.{Instant, LocalDate}
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class ObligationsAndSubmissionsControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with MockitoSugar
    with TestOrgDataFixture
    with UKTRDataFixture {

  val fromDate = "2024-01-01"
  val toDate   = "2024-12-31"

  implicit class AwaitFuture(fut: Future[Result]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err mustEqual expected
    }
  }

  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[ObligationsAndSubmissionsRepository].toInstance(mockOasRepository),
        inject.bind[OrganisationService].toInstance(mockOrgService)
      )
      .build()

  private def createRequest(plrId: String = validPlrId, fromDate: String = fromDate, toDate: String = toDate) =
    FakeRequest(GET, routes.ObligationsAndSubmissionsController.getObligationsAndSubmissions(fromDate, toDate).url)
      .withHeaders(authHeader, "X-Pillar2-Id" -> plrId)

  "Obligations and Submissions" - {
    "when requesting obligations and submissions" - {
      "should return OK with successful response for domestic only organisation" in {
        val domesticOrg = testOrganisation.copy(
          organisation = testOrganisation.organisation.copy(
            orgDetails = testOrganisation.organisation.orgDetails.copy(domesticOnly = true)
          )
        )

        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrg))
        when(mockOasRepository.findAllSubmissionsByPillar2Id(anyString())).thenReturn(
          Future.successful(
            Seq(
              ObligationsAndSubmissionsMongoSubmission(
                _id = new ObjectId,
                submissionId = new ObjectId,
                pillar2Id = validPlrId,
                submissionType = UKTR,
                submittedAt = Instant.now()
              )
            )
          )
        )

        val result = route(app, createRequest()).get
        status(result) mustBe OK

        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails").as[Seq[AccountingPeriodDetails]].head.obligations.length mustBe 1
      }

      "should return OK with successful response for non-domestic organisation" in {
        val nonDomesticOrg = testOrganisation.copy(
          organisation = testOrganisation.organisation.copy(
            orgDetails = testOrganisation.organisation.orgDetails.copy(domesticOnly = false)
          )
        )

        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrg))
        when(mockOasRepository.findAllSubmissionsByPillar2Id(anyString())).thenReturn(
          Future.successful(
            Seq(
              ObligationsAndSubmissionsMongoSubmission(
                _id = new ObjectId,
                submissionId = new ObjectId,
                pillar2Id = validPlrId,
                submissionType = ORN,
                submittedAt = Instant.now()
              )
            )
          )
        )

        val result = route(app, createRequest()).get
        status(result) mustBe OK

        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails").as[Seq[AccountingPeriodDetails]].head.obligations.length mustBe 2
      }

      "should handle submissions with country code for ORN submission type" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockOasRepository.findAllSubmissionsByPillar2Id(anyString())).thenReturn(
          Future.successful(
            Seq(
              ObligationsAndSubmissionsMongoSubmission(
                _id = new ObjectId,
                submissionId = new ObjectId,
                pillar2Id = validPlrId,
                submissionType = ORN,
                submittedAt = Instant.now()
              )
            )
          )
        )

        val result = route(app, createRequest()).get
        status(result) mustBe OK

        val jsonResponse = contentAsJson(result)
        val submissions  = (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "submissions").as[Seq[Submission]]
        submissions.head.country.value mustBe "FR"
      }

      "should set canAmend to false when current date is after due date" in {
        val pastDueOrg = testOrganisation.copy(
          organisation = testOrganisation.organisation.copy(
            accountingPeriod = testOrganisation.organisation.accountingPeriod.copy(
              endDate = LocalDate.now().minusMonths(16)
            )
          )
        )

        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(pastDueOrg))
        when(mockOasRepository.findAllSubmissionsByPillar2Id(anyString())).thenReturn(Future.successful(Seq.empty))

        val result = route(app, createRequest()).get
        status(result) mustBe OK

        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "canAmend").as[Boolean] mustBe false
      }

      "should return the correct response when no submissions exist" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))
        when(mockOasRepository.findAllSubmissionsByPillar2Id(anyString())).thenReturn(Future.successful(Seq.empty))

        val result = route(app, createRequest()).get
        status(result) mustBe OK
        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "submissions").as[Seq[Submission]] mustBe empty
      }

      "should return NoAssociatedDataFound when organisation not found" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

        route(app, createRequest()).value shouldFailWith NoAssociatedDataFound
      }

      "should return RequestCouldNotBeProcessed for invalid date format" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(testOrganisation))

        route(app, createRequest(fromDate = "invalid-date")).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should handle ServerErrorPlrId appropriately" in {
        route(app, createRequest(plrId = ServerErrorPlrId)).value shouldFailWith ETMPInternalServerError
      }
    }
  }
}
