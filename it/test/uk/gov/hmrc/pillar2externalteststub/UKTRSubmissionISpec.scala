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
import play.api.test.Helpers._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.SessionKeys.authToken
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.{OrganisationRepository, UKTRSubmissionRepository}

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture

class UKTRSubmissionISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[UKTRMongoSubmission]
    with UKTRDataFixture
    with TestOrgDataFixture {

  override protected val databaseName: String = "test-uktr-submission-integration"
  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: UKTRSubmissionRepository = app.injector.instanceOf[UKTRSubmissionRepository]
  private val orgRepository: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]
  implicit val ec:                   ExecutionContext         = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                   HeaderCarrier            = HeaderCarrier()

 
  private val testOrg = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod
  )

  private val testOrgWithId = TestOrganisationWithId(
    pillar2Id = validPlrId,
    organisation = testOrg
  )


  private val serverErrorTestOrgWithId = TestOrganisationWithId(
    pillar2Id = ServerErrorPlrId,
    organisation = testOrg
  )

  private val invalidPlrId = "invalidPlr2Id"
  private val invalidOrgWithId = TestOrganisationWithId(
    pillar2Id = invalidPlrId,
    organisation = testOrg
  )


  private val correctNilReturnJson = Json.obj(
    "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
    "accountingPeriodTo"   -> accountingPeriod.endDate.toString,
    "obligationMTT"        -> false,
    "electionUKGAAP"       -> false,
    "liabilities"          -> Json.obj(
      "returnType" -> ReturnType.NIL_RETURN.toString
    )
  )

  private val invalidAccountingPeriodJson = Json.obj(
    "accountingPeriodFrom" -> "2023-01-01",
    "accountingPeriodTo"   -> "2023-12-31",
    "obligationMTT"        -> false,
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

  private val emptyLiableEntitiesJson = Json.obj(
    "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
    "accountingPeriodTo"   -> accountingPeriod.endDate.toString,
    "obligationMTT"        -> false,
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
      "liableEntities"           -> Json.arr()
    )
  )

  private val invalidAmountsJson = Json.obj(
    "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
    "accountingPeriodTo"   -> accountingPeriod.endDate.toString,
    "obligationMTT"        -> false,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "electionDTTSingleMember"  -> false,
      "electionUTPRSingleMember" -> false,
      "numberSubGroupDTT"        -> 4,
      "numberSubGroupUTPR"       -> 5,
      "totalLiability"           -> -500,
      "totalLiabilityDTT"        -> 10000000000000.99,
      "totalLiabilityIIR"        -> 4000,
      "totalLiabilityUTPR"       -> 10000.99,
      "liableEntities"           -> Json.arr(validLiableEntity)
    )
  )

  private val invalidIdTypeJson = Json.obj(
    "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
    "accountingPeriodTo"   -> accountingPeriod.endDate.toString,
    "obligationMTT"        -> false,
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
      "liableEntities"           -> Json.arr(
        validLiableEntity.as[JsObject] ++ Json.obj("idType" -> "INVALID")
      )
    )
  )

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"     -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled" -> false
      )
      .build()

  private def updateSubmissionWithCorrectAccountingPeriod(submission: UKTRSubmission): UKTRSubmission = {
  
    val jsonSubmission = Json.toJson(submission).as[JsObject]
   
    val updatedJson = jsonSubmission ++ Json.obj(
      "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
      "accountingPeriodTo" -> accountingPeriod.endDate.toString
    )
   
    submission match {
      case _: UKTRNilReturn => Json.fromJson[UKTRNilReturn](updatedJson).get
      case _ => Json.fromJson[UKTRLiabilityReturn](updatedJson).get
    }
  }

  private def submitUKTR(submission: UKTRSubmission, pillar2Id: String): HttpResponse = {
    val updatedSubmission = updateSubmissionWithCorrectAccountingPeriod(submission)
    httpClient
      .post(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(Json.toJson(updatedSubmission))
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }

  private def submitUKTRWithoutAuth(submission: UKTRSubmission, pillar2Id: String): HttpResponse = {
    val updatedSubmission = updateSubmissionWithCorrectAccountingPeriod(submission)
    httpClient
      .post(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(Json.toJson(updatedSubmission))
      .setHeader("X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }

  private def submitCustomPayload(payload: JsObject, pillar2Id: String): HttpResponse = {
    httpClient
      .post(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(payload)
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }
  
  private def amendUKTR(submission: UKTRSubmission, pillar2Id: String): HttpResponse = {
    val updatedSubmission = updateSubmissionWithCorrectAccountingPeriod(submission)
    httpClient
      .put(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(Json.toJson(updatedSubmission))
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }

  private def amendUKTRWithoutAuth(submission: UKTRSubmission, pillar2Id: String): HttpResponse = {
    val updatedSubmission = updateSubmissionWithCorrectAccountingPeriod(submission)
    httpClient
      .put(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(Json.toJson(updatedSubmission))
      .setHeader("X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }
 
  private def submitNilReturn(pillar2Id: String): HttpResponse = {
    httpClient
      .post(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(correctNilReturnJson)
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }

 
  private def amendNilReturn(pillar2Id: String): HttpResponse = {
    httpClient
      .put(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
      .withBody(correctNilReturnJson)
      .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> pillar2Id)
      .execute[HttpResponse]
      .futureValue
  }

  private def setupTestOrganisations(): Unit = {

    val _ = Await.result(orgRepository.insert(testOrgWithId), 5.seconds)
    
   
    val _ = Await.result(orgRepository.insert(serverErrorTestOrgWithId), 5.seconds)
    
  
    val _ = Await.result(orgRepository.insert(invalidOrgWithId), 5.seconds)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()

    repository.collection.drop()
    orgRepository.collection.drop()
    

    orgRepository.ensureIndexes().futureValue
    repository.ensureIndexes().futureValue
    
  
    setupTestOrganisations()
  }

  "UKTR endpoints" should {
    "handle liability returns" should {
      "successfully submit a new liability return" in {
        val response = submitUKTR(liabilitySubmission, validPlrId)
        response.status shouldBe CREATED

        val submission = repository.findByPillar2Id(validPlrId).futureValue
        submission shouldBe defined
      }

      "successfully amend an existing liability return" in {
     
        val createResponse = submitUKTR(liabilitySubmission, validPlrId)
        createResponse.status shouldBe CREATED
        
       
        val updatedBody = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject]).get
        val response = amendUKTR(updatedBody, validPlrId)
        response.status shouldBe OK
        
        val latestSubmission = repository.findByPillar2Id(validPlrId).futureValue
        latestSubmission shouldBe defined
      }

      "return 422 when trying to amend non-existent liability return" in {
        val response = amendUKTR(liabilitySubmission, "nonExistentId")

        response.status shouldBe UNPROCESSABLE_ENTITY
        (response.json \ "errors" \ "code").as[String] shouldBe "002" 
        (response.json \ "errors" \ "text").as[String] shouldBe "Pillar2 ID Missing or Invalid"
      }

      "return 422 when trying to amend non-existent nil return" in {
        val response = amendUKTR(nilSubmission, "nonExistentId")

        response.status shouldBe UNPROCESSABLE_ENTITY
        (response.json \ "errors" \ "code").as[String] shouldBe "002"
        (response.json \ "errors" \ "text").as[String] shouldBe "Pillar2 ID Missing or Invalid"
      }
    }

    "handle nil returns" should {
      "successfully submit a new nil return" in {
        val response = submitNilReturn(validPlrId)
        response.status shouldBe CREATED

        val submission = repository.findByPillar2Id(validPlrId).futureValue
        submission shouldBe defined
      }

      "successfully amend an existing nil return" in {
     
        val createResponse = submitNilReturn(validPlrId)
        createResponse.status shouldBe CREATED
        
    
        val response = amendNilReturn(validPlrId)
        response.status shouldBe OK
      }
    }

    "handle error cases" should {
      "return 400 for invalid JSON" in {
        val response = httpClient
          .post(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
          .withBody(Json.obj("invalid" -> "data"))
          .setHeader("Authorization" -> authToken, "X-Pillar2-Id" -> validPlrId)
          .execute[HttpResponse]
          .futureValue

        response.status shouldBe BAD_REQUEST
      }

      "return 422 when Pillar2 ID header is missing" in {
        val response = httpClient
          .post(url"$baseUrl/RESTAdapter/plr/uk-tax-return")
          .withBody(Json.toJson(liabilitySubmission))
          .setHeader("Authorization" -> authToken)
          .execute[HttpResponse]
          .futureValue

        response.status shouldBe UNPROCESSABLE_ENTITY
      }

      "return 403 when Authorization header is missing for submission" in {
        val response = submitUKTRWithoutAuth(liabilitySubmission, validPlrId)
        response.status shouldBe FORBIDDEN
      }

      "return 403 when Authorization header is missing for amendment" in {
        val response = amendUKTRWithoutAuth(liabilitySubmission, validPlrId)
        response.status shouldBe FORBIDDEN
      }

      "return 422 for invalid accounting period" in {
        val response = submitCustomPayload(invalidAccountingPeriodJson, validPlrId)
        response.status shouldBe UNPROCESSABLE_ENTITY
      }

      "return 422 for empty liableEntities array" in {
        val response = submitCustomPayload(emptyLiableEntitiesJson, validPlrId)
        response.status shouldBe UNPROCESSABLE_ENTITY
      }

      "return 422 for invalid amounts" in {
        val response = submitCustomPayload(invalidAmountsJson, validPlrId)
        response.status shouldBe UNPROCESSABLE_ENTITY
      }

      "return 422 for invalid ID type" in {
        val response = submitCustomPayload(invalidIdTypeJson, validPlrId)
        response.status shouldBe UNPROCESSABLE_ENTITY
      }

      "return appropriate error for test PLR IDs" in {
        val serverErrorResponse = submitUKTR(liabilitySubmission, ServerErrorPlrId)
        serverErrorResponse.status shouldBe INTERNAL_SERVER_ERROR
      }

      "return 422 when trying to submit with non-existent PLR ID" in {
        val nonExistentPlrId = "XMPLR9999999999"
        val response = submitUKTR(liabilitySubmission, nonExistentPlrId)
        response.status shouldBe UNPROCESSABLE_ENTITY
      }
    }
  }
}