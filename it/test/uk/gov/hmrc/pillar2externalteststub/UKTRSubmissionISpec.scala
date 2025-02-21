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

  override def beforeEach(): Unit = {
    repository.uktrRepo.collection.drop().toFuture().futureValue
    repository.subscriptionRepo.collection.drop().toFuture().futureValue
    ()
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
        submitUKTR(liabilitySubmission, validPlrId).status
        val updatedBody      = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ Json.obj("accountingPeriodFrom" -> "2024-01-01")).get
        val response         = amendUKTR(updatedBody, validPlrId)
        val latestSubmission = repository.findByPillar2Id(validPlrId).futureValue

        response.status shouldBe 200
        latestSubmission.get.data.accountingPeriodFrom shouldEqual LocalDate.of(2024, 1, 1)
      }

      "return 422 when trying to amend non-existent liability return" in {
        val response = amendUKTR(liabilitySubmission, "invalidPlr2Id")

        response.status                                shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
      }

      "return 422 when trying to amend non-existent nil return" in {
        val response = amendUKTR(nilSubmission, "invalidPlr2Id")

        response.status                                shouldBe 422
        (response.json \ "errors" \ "code").as[String] shouldBe "003"
        (response.json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
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
