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

import org.scalatest.BeforeAndAfterEach
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
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Indexes

class UKTRSubmissionISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with BeforeAndAfterEach
    with UKTRDataFixture {

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  private val repository = app.injector.instanceOf[UKTRSubmissionRepository]
  implicit val ec:       ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc:       HeaderCarrier    = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"     -> "mongodb://localhost:27017/test-uktr-submission-integration",
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

  private def createOrganisation(pillar2Id: String, startDate: String, endDate: String): Unit = {
    val response = httpClient
      .post(url"$baseUrl/pillar2/test/organisation/$pillar2Id")
      .withBody(Json.obj(
        "orgDetails" -> Json.obj(
          "domesticOnly" -> true,
          "organisationName" -> "Test Org",
          "registrationDate" -> LocalDate.now().toString
        ),
        "accountingPeriod" -> Json.obj(
          "startDate" -> startDate,
          "endDate" -> endDate
        )
      ))
      .execute[HttpResponse]
      .futureValue
      
    if (response.status != 201) {
      throw new IllegalStateException(s"Failed to create organization for test: ${response.status}")
    }
  }
  
  private def deleteOrganisation(pillar2Id: String): Unit = {
    // Ignore the response status as the organization might not exist
    val response = httpClient
      .delete(url"$baseUrl/pillar2/test/organisation/$pillar2Id")
      .execute[HttpResponse]
      .futureValue
    
    // Log the status but don't use the result
    println(s"Deleted organization $pillar2Id with status: ${response.status}")
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    repository.uktrRepo.collection.drop().toFuture().futureValue
    
    // Delete the organization if it exists, then create a new one
    deleteOrganisation(validPlrId)
    
    // Create an organization with the accounting period matching the test data
    createOrganisation(validPlrId, "2024-08-14", "2024-12-14")
  }

  "UKTR endpoints" should {
    "handle liability returns" should {
      "successfully submit a new liability return" in {
        val response = submitUKTR(liabilitySubmission, validPlrId)
        response.status shouldBe 201

        val submission = repository.findByPillar2Id(validPlrId).futureValue
        submission shouldBe defined
      }

      "successfully amend an existing liability return" in {
        submitUKTR(liabilitySubmission, validPlrId).status shouldBe 201
        
        val updatedBody      = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ Json.obj("accountingPeriodFrom" -> "2024-08-14")).get
        val response         = amendUKTR(updatedBody, validPlrId)
        val latestSubmission = repository.findByPillar2Id(validPlrId).futureValue

        response.status shouldBe 200
        latestSubmission.get.data.accountingPeriodFrom shouldEqual LocalDate.of(2024, 8, 14)
      }
      
      "maintain historical records when amending submissions" in {
        // Clear any existing data for this test
        repository.uktrRepo.collection.deleteMany(Filters.eq("pillar2Id", validPlrId)).toFuture().futureValue
        
        // Initial submission
        submitUKTR(liabilitySubmission, validPlrId).status shouldBe 201
        
        // Count submissions before amendment
        val countBefore = repository.uktrRepo.collection.countDocuments(
          Filters.eq("pillar2Id", validPlrId)
        ).toFuture().futureValue
        
        // Amend submission
        val updatedBody = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ 
          Json.obj("accountingPeriodFrom" -> "2024-08-14")).get
        amendUKTR(updatedBody, validPlrId).status shouldBe 200
        
        // Count submissions after amendment
        val countAfter = repository.uktrRepo.collection.countDocuments(
          Filters.eq("pillar2Id", validPlrId)
        ).toFuture().futureValue
        
        // Should have created a new document instead of replacing the old one
        countAfter shouldBe countBefore + 1
        
        // Get all documents and verify amendment flag
        val documents = repository.uktrRepo.collection
          .find(Filters.eq("pillar2Id", validPlrId))
          .sort(Indexes.descending("submittedAt"))
          .toFuture()
          .futureValue
          
        documents.size shouldBe 2
        documents.head.isAmendment shouldBe true
        documents(1).isAmendment shouldBe false
      }

      "return 422 when trying to amend non-existent liability return" in {
        val response = amendUKTR(liabilitySubmission, "XEPLR0000000001")

        response.status                                shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      }

      "return 422 when trying to amend non-existent nil return" in {
        val response = amendUKTR(nilSubmission, "XEPLR0000000001")

        response.status                                shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      }

      "return 422 when accounting period does not match the registered period" in {
        // Create an organization with a different accounting period and different ID
        val testPlrId = "XMPLR0000000001"
        deleteOrganisation(testPlrId)
        createOrganisation(testPlrId, "2024-01-01", "2024-12-31")

        // Then try to submit a UKTR with different accounting period
        val response = submitUKTR(liabilitySubmission, testPlrId)
        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Accounting period (2024-08-14 to 2024-12-14) does not match the registered period (2024-01-01 to 2024-12-31)"
      }

      "return 422 when trying to amend with mismatched accounting period" in {
        // Create an organization with a different ID
        val testPlrId = "XMPLR0000000002"
        deleteOrganisation(testPlrId)
        createOrganisation(testPlrId, "2024-08-14", "2024-12-14")

        // Submit initial UKTR
        val submitResponse = submitUKTR(liabilitySubmission, testPlrId)
        submitResponse.status shouldBe 201

        // Try to amend with different accounting period
        val amendBody = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ Json.obj(
          "accountingPeriodFrom" -> "2024-01-01",
          "accountingPeriodTo" -> "2024-12-31"
        )).get

        val response = amendUKTR(amendBody, testPlrId)
        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Accounting period (2024-01-01 to 2024-12-31) does not match the registered period (2024-08-14 to 2024-12-14)"
      }
    }

    "handle nil returns" should {
      "successfully submit a new nil return" in {
        val response = submitUKTR(nilSubmission, validPlrId)
        response.status shouldBe 201

        val submission = repository.findByPillar2Id(validPlrId).futureValue
        submission shouldBe defined
      }

      "successfully amend an existing nil return" in {
        submitUKTR(nilSubmission, validPlrId).status shouldBe 201

        val response = amendUKTR(nilSubmission, validPlrId)
        response.status shouldBe 200
      }
    }

    "handle error cases" should {
      "return 400 for invalid JSON" in {
        val response = httpClient
          .post(url"$baseUrl/RESTAdapter/PLR/UKTaxReturn")
          .withBody(Json.obj("invalid" -> "data"))
          .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> validPlrId)
          .execute[HttpResponse]
          .futureValue

        response.status shouldBe 400
      }

      "return 422 when MTT values are provided for domestic-only groups" in {
        val submissionJson = validRequestBody.as[JsObject] ++ Json.obj(
          "obligationMTT" -> true,
          "liabilities" -> Json.obj(
            "electionDTTSingleMember" -> false,
            "electionUTPRSingleMember" -> false,
            "numberSubGroupDTT" -> 4,
            "numberSubGroupUTPR" -> 5,
            "totalLiability" -> 10000.99,
            "totalLiabilityDTT" -> 5000.99,
            "totalLiabilityIIR" -> 5000.00,
            "totalLiabilityUTPR" -> 3000.00,
            "liableEntities" -> Json.arr(validLiableEntity)
          )
        )
        val submissionWithMTT = submissionJson.as[UKTRSubmission]

        val response = submitUKTR(submissionWithMTT, validPlrId)
        response.status shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "093"
        (response.json \ "errors" \ "text").as[String] shouldBe "obligationMTT cannot be true for a domestic-only group"
      }

      "accept submission with only DTT values for domestic-only groups" in {
        val submissionJson = validRequestBody.as[JsObject] ++ Json.obj(
          "obligationMTT" -> false,
          "liabilities" -> Json.obj(
            "electionDTTSingleMember" -> false,
            "electionUTPRSingleMember" -> false,
            "numberSubGroupDTT" -> 4,
            "numberSubGroupUTPR" -> 5,
            "totalLiability" -> 5000.00,
            "totalLiabilityDTT" -> 5000.00,
            "totalLiabilityIIR" -> 0,
            "totalLiabilityUTPR" -> 0,
            "liableEntities" -> Json.arr(validLiableEntity)
          )
        )
        val submissionWithDTTOnly = submissionJson.as[UKTRSubmission]

        val response = submitUKTR(submissionWithDTTOnly, validPlrId)
        response.status shouldBe 201
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
    }
  }
}
