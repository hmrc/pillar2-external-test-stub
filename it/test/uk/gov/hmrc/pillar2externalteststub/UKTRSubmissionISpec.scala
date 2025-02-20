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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper._
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{AccountingPeriod, OrgDetails, TestOrganisation, TestOrganisationWithId}
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.{OrganisationRepository, UKTRSubmissionRepository}

import scala.concurrent.ExecutionContext

class UKTRSubmissionISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[JsObject]
    with UKTRDataFixture
    with play.api.Logging {

  override protected val databaseName: String = "test-uktr-submission-integration"
  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: UKTRSubmissionRepository = app.injector.instanceOf[UKTRSubmissionRepository]
  private val orgRepository = app.injector.instanceOf[OrganisationRepository]
  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"     -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled" -> false
      )
      .build()

  private def submitUKTR(submission: UKTRSubmission, pillar2Id: String): HttpResponse =
    httpClient
      .post(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
      .withBody(Json.toJson(submission))
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue

  private def amendUKTR(submission: UKTRSubmission, pillar2Id: String): HttpResponse =
    httpClient
      .put(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
      .withBody(Json.toJson(submission))
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue

  override def beforeEach(): Unit = {
    super.beforeEach()
    
    // Drop collections before each test
    repository.collection.drop().toFuture().futureValue
    orgRepository.collection.drop().toFuture().futureValue
    
    // Create indexes for UKTR submissions
    val indexes = Seq(
      org.mongodb.scala.model.IndexModel(
        org.mongodb.scala.model.Indexes.ascending("pillar2Id"),
        org.mongodb.scala.model.IndexOptions()
          .name("uktr_submissions_pillar2Id_idx")
          .sparse(true)
          .background(true)
      ),
      org.mongodb.scala.model.IndexModel(
        org.mongodb.scala.model.Indexes.ascending("createdAt"),
        org.mongodb.scala.model.IndexOptions()
          .name("createdAtTTL")
          .expireAfter(28, java.util.concurrent.TimeUnit.DAYS)
          .background(true)
      )
    )
    
    // Create indexes and verify
    repository.collection.createIndexes(indexes).toFuture().futureValue
    
    // Create organization index
    orgRepository.collection.createIndex(org.mongodb.scala.model.Indexes.ascending("pillar2Id")).toFuture().futureValue
    
    // Create test organization
    val testOrg = TestOrganisationWithId(
      pillar2Id = pillar2Id,
      organisation = TestOrganisation(
        orgDetails = OrgDetails(
          domesticOnly = false,
          organisationName = "Test Org",
          registrationDate = liabilitySubmission.accountingPeriodFrom
        ),
        accountingPeriod = AccountingPeriod(
          startDate = liabilitySubmission.accountingPeriodFrom,
          endDate = liabilitySubmission.accountingPeriodTo
        ),
        lastUpdated = java.time.Instant.now()
      )
    )
    
    // Insert test organization
    if (!orgRepository.insert(testOrg).futureValue) {
      fail("Failed to insert test organization")
    }
  }

  "UKTR endpoints" should {
    "handle liability returns" should {
      "successfully submit a new liability return" in {
        val response = submitUKTR(liabilitySubmission, pillar2Id)
        response.status shouldBe 201

        val submission = repository.findByPillar2Id(pillar2Id).futureValue
        submission shouldBe Right(Some(Json.toJson(liabilitySubmission).as[JsObject]))
      }

      "successfully amend an existing liability return" in {
        submitUKTR(liabilitySubmission, pillar2Id).status shouldBe 201
        val updatedBody      = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ Json.obj("accountingPeriodFrom" -> "2024-01-01")).get
        val response         = amendUKTR(updatedBody, pillar2Id)
        val latestSubmission = repository.findByPillar2Id(pillar2Id).futureValue

        response.status shouldBe 200
        latestSubmission shouldBe Right(Some(Json.toJson(updatedBody).as[JsObject]))
      }

      "return 422 when trying to amend non-existent liability return" in {
        val response = amendUKTR(liabilitySubmission, "invalidPlr2Id")

        response.status                                shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Organisation not found"
      }

      "return 422 when trying to amend non-existent nil return" in {
        val response = amendUKTR(nilSubmission, "invalidPlr2Id")

        response.status                                shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Organisation not found"
      }

      "return 422 when trying to amend with invalid data" in {
        // First submit a valid return
        submitUKTR(liabilitySubmission, pillar2Id).status shouldBe 201

        // Try to amend with invalid data
        val invalidSubmission = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => 
            lr.copy(
              accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1) // Invalid period
            )
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        
        val response = amendUKTR(invalidSubmission, pillar2Id)
        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Accounting period does not match registered period"
      }

      "handle database errors during amendment" in {
        // First submit a valid return
        submitUKTR(liabilitySubmission, pillar2Id).status shouldBe 201

        // Force a database error by using a special test ID
        val response = amendUKTR(liabilitySubmission, ServerErrorPlrId)
        response.status shouldBe 500
        (response.json \ "error" \ "code").as[String] shouldBe "500"
      }
    }

    "handle nil returns" should {
      "successfully submit a new nil return" in {
        val response = submitUKTR(nilSubmission, pillar2Id)
        response.status shouldBe 201

        val submission = repository.findByPillar2Id(pillar2Id).futureValue
        submission shouldBe Right(Some(Json.toJson(nilSubmission).as[JsObject]))
      }

      "successfully amend an existing nil return" in {
        submitUKTR(nilSubmission, pillar2Id).status shouldBe 201

        val response = amendUKTR(nilSubmission, pillar2Id)
        response.status shouldBe 200
      }

      "return 422 when trying to amend nil return with invalid data" in {
        // First submit a valid nil return
        submitUKTR(nilSubmission, pillar2Id).status shouldBe 201

        // Try to amend with invalid data
        val invalidNilSubmission = nilSubmission match {
          case nr: UKTRNilReturn => 
            nr.copy(
              accountingPeriodFrom = nr.accountingPeriodFrom.plusYears(1) // Invalid period
            )
          case _ => fail("Expected UKTRNilReturn")
        }
        
        val response = amendUKTR(invalidNilSubmission, pillar2Id)
        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Accounting period does not match registered period"
      }

      "handle database errors during nil return amendment" in {
        // First submit a valid nil return
        submitUKTR(nilSubmission, pillar2Id).status shouldBe 201

        // Force a database error by using a special test ID
        val response = amendUKTR(nilSubmission, ServerErrorPlrId)
        response.status shouldBe 500
        (response.json \ "error" \ "code").as[String] shouldBe "500"
      }

      "successfully amend a nil return with valid data" in {
        // First submit a valid nil return
        submitUKTR(nilSubmission, pillar2Id).status shouldBe 201

        // Amend with valid data
        val updatedNilSubmission = nilSubmission match {
          case nr: UKTRNilReturn => 
            nr.copy(
              electionUKGAAP = !nr.electionUKGAAP // Toggle the UKGAAP election
            )
          case _ => fail("Expected UKTRNilReturn")
        }
        
        val response = amendUKTR(updatedNilSubmission, pillar2Id)
        response.status shouldBe 200
        
        // Verify the amendment was saved
        val savedSubmission = repository.findByPillar2Id(pillar2Id).futureValue
        savedSubmission shouldBe Right(Some(Json.toJson(updatedNilSubmission).as[JsObject]))
      }
    }

    "handle error cases" should {
      "return 400 for invalid JSON" in {
        val response = httpClient
          .post(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
          .withBody(Json.obj("invalid" -> "data"))
          .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
          .execute[HttpResponse]
          .futureValue

        response.status shouldBe 400
      }

      "return 422 when Pillar2 ID header is missing" in {
        val response = httpClient
          .post(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
          .withBody(Json.toJson(liabilitySubmission))
          .setHeader("Authorization" -> authToken)
          .execute[HttpResponse]
          .futureValue

        response.status shouldBe 422
      }

      "return appropriate error for test PLR IDs" in {
        val serverErrorResponse = submitUKTR(liabilitySubmission, ServerErrorPlrId)
        serverErrorResponse.status shouldBe 500
      }

      "return 422 when organization not found" in {
        val response = amendUKTR(liabilitySubmission, "NONEXISTENT")

        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Organisation not found"
      }

      "return 422 when accounting period does not match" in {
        submitUKTR(liabilitySubmission, pillar2Id).status shouldBe 201

        val mismatchedSubmission = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1))
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val response = amendUKTR(mismatchedSubmission, pillar2Id)

        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Accounting period does not match registered period"
      }

      "return 422 when MTT values in domestic-only groups" in {
        // Create a domestic-only organization
        val domesticOrg = TestOrganisationWithId(
          pillar2Id = "DOMESTIC123",
          organisation = TestOrganisation(
            orgDetails = OrgDetails(
              domesticOnly = true,
              organisationName = "Domestic Test Org",
              registrationDate = liabilitySubmission.accountingPeriodFrom
            ),
            accountingPeriod = AccountingPeriod(
              startDate = liabilitySubmission.accountingPeriodFrom,
              endDate = liabilitySubmission.accountingPeriodTo
            ),
            lastUpdated = java.time.Instant.now()
          )
        )
        
        // Insert domestic organization
        orgRepository.insert(domesticOrg).futureValue shouldBe true

        // Submit with MTT = true
        val submissionWithMTT = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val response = submitUKTR(submissionWithMTT, "DOMESTIC123")

        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "093"
        (response.json \ "errors" \ "text").as[String] shouldBe "obligationMTT cannot be true for a domestic-only group"
      }
    }
  }
}
