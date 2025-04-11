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

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.mockito.stubbing.OngoingStubbing
import org.mongodb.scala.bson.ObjectId
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsValue
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.{Application, inject}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.{TestOrgDataFixture, UKTRDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{ETMPInternalServerError, NoAssociatedDataFound, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.ObligationStatus.{Fulfilled, Open}
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.ObligationType.{GIR, UKTR}
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.{AccountingPeriod, ObligationsAndSubmissionsMongoSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.ObligationsAndSubmissionsRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.{Instant, LocalDate}
import scala.concurrent.Future

class ObligationsAndSubmissionsControllerSpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with OptionValues
    with MockitoSugar
    with TestOrgDataFixture
    with UKTRDataFixture {

  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]

  def mockBySubmissionType(subType: SubmissionType): OngoingStubbing[Future[Seq[ObligationsAndSubmissionsMongoSubmission]]] =
    when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])).thenReturn(
      Future.successful(
        Seq(
          ObligationsAndSubmissionsMongoSubmission(
            _id = new ObjectId,
            submissionId = new ObjectId,
            pillar2Id = validPlrId,
            accountingPeriod = AccountingPeriod(accountingPeriod.startDate, accountingPeriod.endDate),
            submissionType = subType,
            submittedAt = Instant.now()
          )
        )
      )
    )

  def mockMultipleAccountingPeriods(): OngoingStubbing[Future[Seq[ObligationsAndSubmissionsMongoSubmission]]] = {
    val period1 = AccountingPeriod(
      startDate = LocalDate.of(2023, 1, 1),
      endDate = LocalDate.of(2023, 12, 31)
    )

    val period2 = AccountingPeriod(
      startDate = LocalDate.of(2024, 1, 1),
      endDate = LocalDate.of(2024, 12, 31)
    )

    when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])).thenReturn(
      Future.successful(
        Seq(
          ObligationsAndSubmissionsMongoSubmission(
            _id = new ObjectId,
            submissionId = new ObjectId,
            pillar2Id = validPlrId,
            accountingPeriod = period1,
            submissionType = UKTR_CREATE,
            submittedAt = Instant.now()
          ),
          ObligationsAndSubmissionsMongoSubmission(
            _id = new ObjectId,
            submissionId = new ObjectId,
            pillar2Id = validPlrId,
            accountingPeriod = period2,
            submissionType = BTN,
            submittedAt = Instant.now()
          ),
          ObligationsAndSubmissionsMongoSubmission(
            _id = new ObjectId,
            submissionId = new ObjectId,
            pillar2Id = validPlrId,
            accountingPeriod = period1, // Another submission for period1
            submissionType = ORN_CREATE,
            submittedAt = Instant.now()
          )
        )
      )
    )
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .overrides(
        inject.bind[ObligationsAndSubmissionsRepository].toInstance(mockOasRepository),
        inject.bind[OrganisationService].toInstance(mockOrgService)
      )
      .build()

  private def createRequest(
    plrId:    String = validPlrId,
    fromDate: String = accountingPeriod.startDate.toString,
    toDate:   String = accountingPeriod.endDate.toString
  ) =
    FakeRequest(GET, routes.ObligationsAndSubmissionsController.getObligationsAndSubmissions(fromDate, toDate).url)
      .withHeaders(authHeader, "X-Pillar2-Id" -> plrId)

  "Obligations and Submissions" - {
    "when requesting Obligations and Submissions" - {
      "should return OK with successful response for domestic-only organisation" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))
        mockBySubmissionType(UKTR_CREATE)

        val result = route(app, createRequest()).value
        status(result) mustBe OK

        val jsonResponse         = contentAsJson(result)
        val obligations          = (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations").as[Seq[Obligation]]
        val firstOligationType   = obligations.head.obligationType
        val secondObligationType = obligations(1).obligationType

        obligations.size mustBe 2
        firstOligationType mustBe UKTR
        secondObligationType mustBe GIR
      }

      "should return OK with successful response for non-domestic organisation" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
        mockBySubmissionType(ORN_CREATE)

        val result = route(app, createRequest()).value
        status(result) mustBe OK

        val jsonResponse         = contentAsJson(result)
        val obligations          = (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations").as[Seq[Obligation]]
        val firstOligationType   = obligations.head.obligationType
        val secondObligationType = obligations(1).obligationType

        obligations.size mustBe 2
        firstOligationType mustBe UKTR
        secondObligationType mustBe GIR
      }

      "should handle submissions with country code for ORN submission type" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
        mockBySubmissionType(ORN_CREATE)

        val result = route(app, createRequest()).value
        status(result) mustBe OK

        val jsonResponse = contentAsJson(result)
        val submissions  = (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 1 \ "submissions").as[Seq[Submission]]
        submissions.head.country.value mustBe "FR"
      }

      "should conditionally show the GIR obligation" - {
        "not show if the last submission is a BTN" in {
          when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))
          mockBySubmissionType(UKTR_CREATE)
          mockBySubmissionType(BTN)

          val result = route(app, createRequest()).value
          status(result) mustBe OK

          val jsonResponse = contentAsJson(result)
          val obligations  = (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations").as[Seq[Obligation]]

          obligations.size mustBe 1
        }
        "show if the last submission is not a BTN" in {
          when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))
          mockBySubmissionType(BTN)
          mockBySubmissionType(UKTR_CREATE)

          val result = route(app, createRequest()).value
          status(result) mustBe OK

          val jsonResponse = contentAsJson(result)
          val obligations  = (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations").as[Seq[Obligation]]

          obligations.size mustBe 2
        }
      }

      "should set canAmend to false when current date is after due date" in {
        val pastDueOrg = domesticOrganisation.copy(
          organisation = domesticOrganisation.organisation.copy(
            accountingPeriod = domesticOrganisation.organisation.accountingPeriod.copy(
              startDate = LocalDate.of(2022, 1, 1),
              endDate = LocalDate.of(2022, 12, 31)
            )
          )
        )

        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(pastDueOrg))
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])).thenReturn(Future.successful(Seq.empty))

        val result = route(app, createRequest(fromDate = "2022-01-01", toDate = "2023-01-01")).value
        status(result) mustBe OK

        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "canAmend").as[Boolean] mustBe false
      }

      "should return the correct response when no submissions exist" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])).thenReturn(Future.successful(Seq.empty))

        val result = route(app, createRequest()).value
        status(result) mustBe OK
        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "submissions").as[Seq[Submission]] mustBe empty
      }

      "should return ObligationStatus Open when there are no submissions" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])).thenReturn(Future.successful(Seq.empty))

        val result = route(app, createRequest()).value

        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "status").as[ObligationStatus] mustBe Open
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 1 \ "status").as[ObligationStatus] mustBe Open
      }

      "should return ObligationStatus Fulfilled when there are no submissions" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
        mockBySubmissionType(UKTR_CREATE)

        val result = route(app, createRequest()).value

        val jsonResponse = contentAsJson(result)
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 0 \ "status").as[ObligationStatus] mustBe Fulfilled
        (jsonResponse \ "success" \ "accountingPeriodDetails" \ 0 \ "obligations" \ 1 \ "status").as[ObligationStatus] mustBe Open
      }

      "should return NoAssociatedDataFound when organisation not found" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

        route(app, createRequest()).value shouldFailWith NoAssociatedDataFound
      }

      "should return RequestCouldNotBeProcessed for invalid date format" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        route(app, createRequest(fromDate = "invalid-date")).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should return RequestCouldNotBeProcessed when fromDate is after toDate" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        // Use a fromDate that is learly after toDate
        val futureFromDate = LocalDate.now().plusYears(1).toString
        val pastToDate     = LocalDate.now().minusYears(1).toString

        route(app, createRequest(fromDate = futureFromDate, toDate = pastToDate)).value shouldFailWith RequestCouldNotBeProcessed
      }

      "should handle ServerErrorPlrId appropriately" in {
        route(app, createRequest(plrId = ServerErrorPlrId)).value shouldFailWith ETMPInternalServerError
      }

      "should group submissions by accounting period" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
        mockMultipleAccountingPeriods()

        val result = route(app, createRequest()).value
        status(result) mustBe OK

        val jsonResponse            = contentAsJson(result)
        val accountingPeriodDetails = (jsonResponse \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

        // Should have two accounting periods
        accountingPeriodDetails.size mustBe 2

        // First accounting period (2023) should have 2 submissions (UKTR and ORN)
        val period1 = accountingPeriodDetails.find(period => (period \ "startDate").as[String] == "2023-01-01").value

        val period1Obligations    = (period1 \ "obligations").as[Seq[JsValue]]
        val period1GIRSubmissions = (period1Obligations(1) \ "submissions").as[Seq[JsValue]]
        period1GIRSubmissions.size mustBe 1
        (period1GIRSubmissions.head \ "submissionType").as[String] mustBe "ORN_CREATE"

        // Second accounting period (2024) should have 1 submission (BTN)
        val period2 = accountingPeriodDetails.find(period => (period \ "startDate").as[String] == "2024-01-01").value

        val period2Obligations   = (period2 \ "obligations").as[Seq[JsValue]]
        val period2P2Submissions = (period2Obligations.head \ "submissions").as[Seq[JsValue]]
        period2P2Submissions.size mustBe 1
        (period2P2Submissions.head \ "submissionType").as[String] mustBe "BTN"
      }

      "should create obligations for each accounting period with correct status" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
        mockMultipleAccountingPeriods()

        val result = route(app, createRequest()).value
        status(result) mustBe OK

        val jsonResponse            = contentAsJson(result)
        val accountingPeriodDetails = (jsonResponse \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

        // First period should have Fulfilled status for Pillar2TaxReturn due to UKTR submission
        val period1 = accountingPeriodDetails.find(period => (period \ "startDate").as[String] == "2023-01-01").value

        val period1Obligations = (period1 \ "obligations").as[Seq[JsValue]]
        (period1Obligations.head \ "status").as[String] mustBe "Fulfilled"
        (period1Obligations.head \ "obligationType").as[String] mustBe "UKTR"

        // Second period should have Fulfilled status for Pillar2TaxReturn due to BTN submission
        val period2 = accountingPeriodDetails.find(period => (period \ "startDate").as[String] == "2024-01-01").value

        val period2Obligations = (period2 \ "obligations").as[Seq[JsValue]]
        (period2Obligations.head \ "status").as[String] mustBe "Fulfilled"
        (period2Obligations.head \ "obligationType").as[String] mustBe "UKTR"
      }

      "should use organisation's default accounting period when no submissions exist" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])).thenReturn(Future.successful(Seq.empty))

        val result = route(app, createRequest()).value
        status(result) mustBe OK

        val jsonResponse            = contentAsJson(result)
        val accountingPeriodDetails = (jsonResponse \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

        // Should have only one accounting period (the default one)
        accountingPeriodDetails.size mustBe 1

        // Should match the organisation's default accounting period
        (accountingPeriodDetails.head \ "startDate").as[String] mustBe domesticOrganisation.organisation.accountingPeriod.startDate.toString
        (accountingPeriodDetails.head \ "endDate").as[String] mustBe domesticOrganisation.organisation.accountingPeriod.endDate.toString
      }
    }
  }
}
