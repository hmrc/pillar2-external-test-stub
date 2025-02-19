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
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class UKTRSubmissionISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[UKTRMongoSubmission]
    with UKTRDataFixture {

  override protected val databaseName: String = "test-uktr-submission-integration"
  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: UKTRSubmissionRepository = app.injector.instanceOf[UKTRSubmissionRepository]
  implicit val ec:                   ExecutionContext         = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                   HeaderCarrier            = HeaderCarrier()

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
    repository.collection.drop()
    ()
  }

  "UKTR endpoints" should {
    "handle liability returns" should {
      "successfully submit a new liability return" in {
        val response = submitUKTR(liabilitySubmission, pillar2Id)
        response.status shouldBe 201

        val submission = repository.findByPillar2Id(pillar2Id).futureValue
        submission shouldBe defined
      }

      "successfully amend an existing liability return" in {
        submitUKTR(liabilitySubmission, pillar2Id).status
        val updatedBody      = Json.fromJson[UKTRSubmission](validRequestBody.as[JsObject] ++ Json.obj("accountingPeriodFrom" -> "2024-01-01")).get
        val response         = amendUKTR(updatedBody, pillar2Id)
        val latestSubmission = repository.findByPillar2Id(pillar2Id).futureValue

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
        val response = submitUKTR(nilSubmission, pillar2Id)
        response.status shouldBe 201

        val submission = repository.findByPillar2Id(pillar2Id).futureValue
        submission shouldBe defined
      }

      "successfully amend an existing nil return" in {
        submitUKTR(nilSubmission, pillar2Id).status shouldBe 201

        val response = amendUKTR(nilSubmission, pillar2Id)
        response.status shouldBe 200
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
    }
  }
}
