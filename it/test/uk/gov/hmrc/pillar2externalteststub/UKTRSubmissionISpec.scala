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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import play.api.http.Status.{CREATED, UNPROCESSABLE_ENTITY}

import scala.concurrent.{ExecutionContext, Future}

class UKTRSubmissionISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[UKTRMongoSubmission]
    with UKTRDataFixture {

  override protected val databaseName: String = "test-uktr-submission-integration"
  override protected lazy val collectionName: String = "uktr-submissions"
  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected lazy val repository = app.injector.instanceOf[UKTRSubmissionRepository]
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

  override protected def prepareDatabase(): Unit = {
    super.prepareDatabase()
    
  
    repository.collection.drop().toFuture().futureValue
    orgRepository.collection.drop().toFuture().futureValue
    
    
    val indexes = Seq(
      org.mongodb.scala.model.IndexModel(
        org.mongodb.scala.model.Indexes.compoundIndex(
          org.mongodb.scala.model.Indexes.ascending("pillar2Id"),
          org.mongodb.scala.model.Indexes.descending("submittedAt")
        ),
        org.mongodb.scala.model.IndexOptions()
          .name("pillar2IdIndex")
          .unique(true)
      ),
      org.mongodb.scala.model.IndexModel(
        org.mongodb.scala.model.Indexes.ascending("submittedAt"),
        org.mongodb.scala.model.IndexOptions()
          .name("submittedAtTTL")
          .expireAfter(28, java.util.concurrent.TimeUnit.DAYS)
          .background(true)
      )
    )
    
   
    repository.collection.createIndexes(indexes).toFuture().futureValue
    
   
    orgRepository.collection.createIndex(org.mongodb.scala.model.Indexes.ascending("pillar2Id")).toFuture().futureValue
    ()
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    

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
    

    if (!orgRepository.insert(testOrg).futureValue) {
      fail("Failed to insert test organization")
    }
  }

  private def submitUKTR(submission: UKTRSubmission, pillar2Id: String)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    httpClient
      .post(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
      .withBody(Json.toJson(submission))
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
  }

  private def amendUKTR(submission: UKTRSubmission, pillar2Id: String)(implicit ec: ExecutionContext): Future[HttpResponse] = {
    httpClient
      .put(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
      .withBody(Json.toJson(submission))
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
  }

  private def invalidLiabilityReturn = liabilitySubmission match {
    case lr: UKTRLiabilityReturn => 
      lr.copy(
        accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1) 
      )
    case _ => fail("Expected UKTRLiabilityReturn")
  }

  private def invalidNilReturn = nilSubmission match {
    case nr: UKTRNilReturn => 
      nr.copy(
        accountingPeriodFrom = nr.accountingPeriodFrom.plusYears(1) 
      )
    case _ => fail("Expected UKTRNilReturn")
  }

  private def invalidAccountingPeriodReturn = liabilitySubmission match {
    case lr: UKTRLiabilityReturn => lr.copy(accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1))
    case _ => fail("Expected UKTRLiabilityReturn")
  }

  "UKTR endpoints" should {
    "handle liability returns" should {
      "successfully submit a new liability return" in {
        val response = submitUKTR(liabilitySubmission, pillar2Id)
        response.futureValue.status shouldBe 201

        val submission = repository.findByPillar2Id(pillar2Id).futureValue
        submission shouldBe defined
        submission.get.data shouldBe liabilitySubmission
      }

      "successfully amend an existing liability return" in {
        submitUKTR(liabilitySubmission, pillar2Id).futureValue.status shouldBe 201
        val updatedBody      = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ Json.obj("accountingPeriodFrom" -> "2024-01-01")).get
        val response         = amendUKTR(updatedBody, pillar2Id)
        val latestSubmission = repository.findByPillar2Id(pillar2Id).futureValue

        response.futureValue.status shouldBe 200
        latestSubmission shouldBe defined
        latestSubmission.get.data shouldBe updatedBody
      }

      "return 422 when trying to amend non-existent liability return" in {
        val response = amendUKTR(liabilitySubmission, "invalidPlr2Id")

        response.futureValue.status                                shouldBe 422
        (response.futureValue.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.futureValue.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      }

      "return 422 when trying to amend non-existent nil return" in {
        val response = amendUKTR(nilSubmission, "invalidPlr2Id")

        response.futureValue.status                                shouldBe 422
        (response.futureValue.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.futureValue.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      }

      "should return 422 when trying to amend with invalid data" in {
        submitUKTR(liabilitySubmission, "XMPLR0012345678").futureValue.status shouldBe CREATED

        val result = amendUKTR(invalidLiabilityReturn, "XMPLR0012345678").futureValue

        result.status shouldBe UNPROCESSABLE_ENTITY
        (result.json \ "errors" \ "code").as[String] shouldBe "003"
        (result.json \ "errors" \ "text").as[String] shouldBe "Accounting period end date must be after start date"
      }

      "handle database errors during amendment" in {
       
        submitUKTR(liabilitySubmission, pillar2Id).futureValue.status shouldBe 201

       
        val response = amendUKTR(liabilitySubmission, ServerErrorPlrId)
        response.futureValue.status shouldBe 500
        (response.futureValue.json \ "error" \ "code").as[String] shouldBe "500"
      }
    }

    "handle nil returns" should {
      "successfully submit a new nil return" in {
        val response = submitUKTR(nilSubmission, pillar2Id)
        response.futureValue.status shouldBe 201

        val submission = repository.findByPillar2Id(pillar2Id).futureValue
        submission shouldBe defined
        submission.get.data shouldBe nilSubmission
      }

      "successfully amend an existing nil return" in {
        submitUKTR(nilSubmission, pillar2Id).futureValue.status shouldBe 201

        val response = amendUKTR(nilSubmission, pillar2Id)
        response.futureValue.status shouldBe 200
      }

      "should return 422 when trying to amend nil return with invalid data" in {
        submitUKTR(nilSubmission, "XMPLR0012345678").futureValue.status shouldBe CREATED

        val result = amendUKTR(invalidNilReturn, "XMPLR0012345678").futureValue

        result.status shouldBe UNPROCESSABLE_ENTITY
        (result.json \ "errors" \ "code").as[String] shouldBe "003"
        (result.json \ "errors" \ "text").as[String] shouldBe "Accounting period end date must be after start date"
      }

      "handle database errors during nil return amendment" in {
       
        submitUKTR(nilSubmission, pillar2Id).futureValue.status shouldBe 201

        
        val response = amendUKTR(nilSubmission, ServerErrorPlrId)
        response.futureValue.status shouldBe 500
        (response.futureValue.json \ "error" \ "code").as[String] shouldBe "500"
      }

      "successfully amend a nil return with valid data" in {
        
        submitUKTR(nilSubmission, pillar2Id).futureValue.status shouldBe 201

      
        val updatedNilSubmission = nilSubmission match {
          case nr: UKTRNilReturn => 
            nr.copy(
              electionUKGAAP = !nr.electionUKGAAP 
            )
          case _ => fail("Expected UKTRNilReturn")
        }
        
        val response = amendUKTR(updatedNilSubmission, pillar2Id)
        response.futureValue.status shouldBe 200
        
      
        val savedSubmission = repository.findByPillar2Id(pillar2Id).futureValue
        savedSubmission shouldBe defined
        savedSubmission.get.data shouldBe updatedNilSubmission
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
        serverErrorResponse.futureValue.status shouldBe 500
      }

      "return 422 when organization not found" in {
        val response = amendUKTR(liabilitySubmission, "NONEXISTENT")

        response.futureValue.status shouldBe 422
        (response.futureValue.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.futureValue.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      }

      "should return 422 when accounting period does not match" in {
        submitUKTR(liabilitySubmission, "XMPLR0012345678").futureValue.status shouldBe CREATED

        val result = amendUKTR(invalidAccountingPeriodReturn, "XMPLR0012345678").futureValue

        result.status shouldBe UNPROCESSABLE_ENTITY
        (result.json \ "errors" \ "code").as[String] shouldBe "003"
        (result.json \ "errors" \ "text").as[String] shouldBe "Accounting period end date must be after start date"
      }

      "return 422 when MTT values in domestic-only groups" in {
      
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
        
       
        orgRepository.insert(domesticOrg).futureValue shouldBe true

       
        val submissionWithMTT = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val response = submitUKTR(submissionWithMTT, "DOMESTIC123")

        response.futureValue.status shouldBe 422
        (response.futureValue.json \ "errors" \ "code").as[String] shouldBe "093"
        (response.futureValue.json \ "errors" \ "text").as[String] shouldBe "obligationMTT cannot be true for a domestic-only group"

        // Test for nil return with MTT
        val nilSubmissionWithMTT = nilSubmission match {
          case nr: UKTRNilReturn => nr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRNilReturn")
        }
        val nilResponse = submitUKTR(nilSubmissionWithMTT, "DOMESTIC123")

        nilResponse.futureValue.status shouldBe 422
        (nilResponse.futureValue.json \ "errors" \ "code").as[String] shouldBe "093"
        (nilResponse.futureValue.json \ "errors" \ "text").as[String] shouldBe "obligationMTT cannot be true for a domestic-only group"
      }
    }
  }
}
