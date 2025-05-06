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

package uk.gov.hmrc.pillar2externalteststub

import org.mongodb.scala.bson.ObjectId
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.helpers.{TestOrgDataFixture, UKTRDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.{AccountingPeriod, ObligationsAndSubmissionsMongoSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.{ObligationsAndSubmissionsRepository, OrganisationRepository}

import java.time.Instant
import scala.concurrent.ExecutionContext
import java.time.LocalDate

class ObligationsAndSubmissionsISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[ObligationsAndSubmissionsMongoSubmission]
    with BeforeAndAfterEach
    with TestOrgDataFixture
    with UKTRDataFixture {

  override protected val databaseName: String = "test-obligations-and-submissions-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository:  ObligationsAndSubmissionsRepository = app.injector.instanceOf[ObligationsAndSubmissionsRepository]
  private val organisationRepository: OrganisationRepository              = app.injector.instanceOf[OrganisationRepository]
  implicit val ec:                    ExecutionContext                    = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                    HeaderCarrier                       = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"             -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled"         -> false,
        "defaultDataExpireInDays" -> 28
      )
      .build()

  private def getObligationsAndSubmissions(
    pillar2Id: String,
    fromDate:  String = accountingPeriod.startDate.toString,
    toDate:    String = accountingPeriod.endDate.toString
  ): HttpResponse =
    httpClient
      .get(url"$baseUrl/RESTAdapter/plr/obligations-and-submissions?fromDate=$fromDate&toDate=$toDate")
      .transform(_.withHttpHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*))
      .execute[HttpResponse]
      .futureValue

  private def insertSubmission(
    pillar2Id:        String,
    submissionType:   SubmissionType,
    accountingPeriod: AccountingPeriod
  ): ObligationsAndSubmissionsMongoSubmission = {
    val submission = ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId,
      submissionId = new ObjectId,
      pillar2Id = pillar2Id,
      accountingPeriod = accountingPeriod,
      submissionType = submissionType,
      ornCountryGir = if (submissionType == ORN_CREATE || submissionType == ORN_AMEND) Some("US") else None,
      submittedAt = Instant.now()
    )
    repository.collection.insertOne(submission).toFuture().futureValue
    submission
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
    organisationRepository.delete(validPlrId).futureValue
    organisationRepository.delete(nonDomesticPlrId).futureValue
    organisationRepository.insert(domesticOrganisation).futureValue
    organisationRepository.insert(nonDomesticOrganisation).futureValue
    ()
  }

  override protected def prepareDatabase(): Unit = {
    repository.collection.drop().toFuture().futureValue
    repository.ensureIndexes().futureValue
    ()
  }

  "ObligationsAndSubmissions endpoint" should {
    "return correct response for domestic organisation with submissions" in {
      // Insert a submission for the domestic organisation
      insertSubmission(
        domesticOrganisation.pillar2Id,
        UKTR_CREATE,
        AccountingPeriod(accountingPeriod.startDate, accountingPeriod.endDate)
      )

      val response = getObligationsAndSubmissions(domesticOrganisation.pillar2Id)
      response.status shouldBe 200

      val json                    = Json.parse(response.body)
      val accountingPeriodDetails = (json \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

      accountingPeriodDetails.size shouldBe 1

      val obligations = (accountingPeriodDetails.head \ "obligations").as[Seq[JsValue]]
      obligations.size shouldBe 2

      // Verify obligations for domestic organisation
      (obligations.head \ "obligationType").as[String] shouldBe "UKTR"
      (obligations(1) \ "obligationType").as[String]   shouldBe "GIR"
      (obligations.head \ "status").as[String]         shouldBe "Fulfilled"
      (obligations(1) \ "status").as[String]           shouldBe "Open"

      // Verify submissions
      val submissions = (obligations.head \ "submissions").as[Seq[JsValue]]
      submissions.size                                 shouldBe 1
      (submissions.head \ "submissionType").as[String] shouldBe "UKTR_CREATE"
    }

    "return correct response for non-domestic organisation with submissions" in {
      // Insert submissions for the non-domestic organisation
      insertSubmission(
        nonDomesticOrganisation.pillar2Id,
        ORN_CREATE,
        AccountingPeriod(accountingPeriod.startDate, accountingPeriod.endDate)
      )

      val response = getObligationsAndSubmissions(nonDomesticOrganisation.pillar2Id)
      response.status shouldBe 200

      val json                    = Json.parse(response.body)
      val accountingPeriodDetails = (json \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

      accountingPeriodDetails.size shouldBe 1

      val obligations = (accountingPeriodDetails.head \ "obligations").as[Seq[JsValue]]
      obligations.size shouldBe 2

      // Verify first obligation is Pillar2TaxReturn with Open status
      (obligations.head \ "obligationType").as[String] shouldBe "UKTR"
      (obligations.head \ "status").as[String]         shouldBe "Open"

      // Verify second obligation is GlobeInformationReturn with Fulfilled status
      (obligations(1) \ "obligationType").as[String] shouldBe "GIR"
      (obligations(1) \ "status").as[String]         shouldBe "Fulfilled"

      // Verify submissions in GIR obligation
      val submissions = (obligations(1) \ "submissions").as[Seq[JsValue]]
      submissions.size                                 shouldBe 1
      (submissions.head \ "submissionType").as[String] shouldBe "ORN_CREATE"
      (submissions.head \ "country").as[String]        shouldBe "US"
    }

    "group submissions by accounting period" in {
      // Define two accounting periods
      val period1 = AccountingPeriod(
        startDate = LocalDate.of(2023, 1, 1),
        endDate = LocalDate.of(2023, 12, 31)
      )

      val period2 = AccountingPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31)
      )

      // Insert submissions for different accounting periods
      insertSubmission(nonDomesticOrganisation.pillar2Id, UKTR_CREATE, period1)
      insertSubmission(nonDomesticOrganisation.pillar2Id, ORN_CREATE, period1)
      insertSubmission(nonDomesticOrganisation.pillar2Id, BTN, period2)

      val response = getObligationsAndSubmissions(
        nonDomesticOrganisation.pillar2Id,
        fromDate = "2023-01-01",
        toDate = "2024-12-31"
      )

      response.status shouldBe 200

      val json                    = Json.parse(response.body)
      val accountingPeriodDetails = (json \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

      // Should have two accounting periods
      accountingPeriodDetails.size shouldBe 2

      // Find period1 details
      val period1Details = accountingPeriodDetails.find(period => (period \ "startDate").as[String] == "2023-01-01").get

      // Find period2 details
      val period2Details = accountingPeriodDetails.find(period => (period \ "startDate").as[String] == "2024-01-01").get

      // Verify period1 has UKTR and ORN submissions
      val period1Obligations = (period1Details \ "obligations").as[Seq[JsValue]]
      val period1P2Status    = (period1Obligations.head \ "status").as[String]
      val period1GIRStatus   = (period1Obligations(1) \ "status").as[String]

      period1P2Status  shouldBe "Fulfilled"
      period1GIRStatus shouldBe "Fulfilled"

      // Verify period2 has BTN submission
      val period2Obligations = (period2Details \ "obligations").as[Seq[JsValue]]
      val period2P2Status    = (period2Obligations.head \ "status").as[String]

      period2P2Status shouldBe "Fulfilled"
    }

    "use organisation's default accounting period when no submissions exist" in {
      val response = getObligationsAndSubmissions(domesticOrganisation.pillar2Id)
      response.status shouldBe 200

      val json                    = Json.parse(response.body)
      val accountingPeriodDetails = (json \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]

      // Should have only one accounting period
      accountingPeriodDetails.size shouldBe 1

      // Period should match org's default period
      (accountingPeriodDetails.head \ "startDate").as[String] shouldBe domesticOrganisation.organisation.accountingPeriod.startDate.toString
      (accountingPeriodDetails.head \ "endDate").as[String]   shouldBe domesticOrganisation.organisation.accountingPeriod.endDate.toString

      // Obligation should be Open
      val obligations = (accountingPeriodDetails.head \ "obligations").as[Seq[JsValue]]
      (obligations.head \ "status").as[String] shouldBe "Open"
    }

    "handle BTN submissions correctly" in {
      // Insert BTN submission
      insertSubmission(
        nonDomesticOrganisation.pillar2Id,
        BTN,
        AccountingPeriod(accountingPeriod.startDate, accountingPeriod.endDate)
      )

      val response = getObligationsAndSubmissions(nonDomesticOrganisation.pillar2Id)
      response.status shouldBe 200

      val json                    = Json.parse(response.body)
      val accountingPeriodDetails = (json \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]
      val obligations             = (accountingPeriodDetails.head \ "obligations").as[Seq[JsValue]]
      val p2Submissions           = (obligations.head \ "submissions").as[Seq[JsValue]]

      obligations.size                                   shouldBe 1
      (obligations.head \ "status").as[String]           shouldBe "Fulfilled"
      (p2Submissions.head \ "submissionType").as[String] shouldBe "BTN"
    }

    "handle error cases correctly" in {
      // Test organisation not found
      val nonExistentPlrIdResponse = getObligationsAndSubmissions("NONEXISTENT")
      val nonExistentPlrIdJson     = Json.parse(nonExistentPlrIdResponse.body)
      (nonExistentPlrIdJson \ "errors" \ "text").as[String] shouldBe "No data found"
      nonExistentPlrIdResponse.status                       shouldBe 422

      // Test invalid date format
      val invalidDateResponse = getObligationsAndSubmissions(
        domesticOrganisation.pillar2Id,
        fromDate = "invalid-date"
      )
      val invalidDateJson = Json.parse(invalidDateResponse.body)
      (invalidDateJson \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      invalidDateResponse.status                       shouldBe 422
      // Test fromDate after toDate
      val invalidDateRangeResponse = getObligationsAndSubmissions(
        domesticOrganisation.pillar2Id,
        fromDate = "2024-12-31",
        toDate = "2024-01-01"
      )
      invalidDateRangeResponse.status shouldBe 422
      val invalidDateRangeJson = Json.parse(invalidDateRangeResponse.body)
      (invalidDateRangeJson \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
    }

    "set canAmend flag correctly based on due date" in {
      // Create accounting period with past due date
      val pastAccountingPeriod = AccountingPeriod(
        startDate = LocalDate.now().minusYears(3),
        endDate = LocalDate.now().minusYears(2).minusDays(1)
      )

      insertSubmission(
        domesticOrganisation.pillar2Id,
        UKTR_CREATE,
        pastAccountingPeriod
      )

      val response = getObligationsAndSubmissions(
        domesticOrganisation.pillar2Id,
        fromDate = pastAccountingPeriod.startDate.toString,
        toDate = pastAccountingPeriod.endDate.toString
      )

      response.status shouldBe 200

      val json                    = Json.parse(response.body)
      val accountingPeriodDetails = (json \ "success" \ "accountingPeriodDetails").as[Seq[JsValue]]
      val obligations             = (accountingPeriodDetails.head \ "obligations").as[Seq[JsValue]]

      // canAmend should be false for past due date
      (obligations.head \ "canAmend").as[Boolean] shouldBe false
    }
  }
}
