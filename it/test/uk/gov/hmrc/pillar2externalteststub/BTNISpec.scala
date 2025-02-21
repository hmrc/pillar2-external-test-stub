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
import org.scalatest.BeforeAndAfterEach
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.models.btn.mongo.BTNSubmission
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.repositories.BTNSubmissionRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class BTNISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[BTNSubmission]
    with BeforeAndAfterEach {

  override protected val databaseName: String = "test-btn-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl = s"http://localhost:$port"
  override protected val repository = app.injector.instanceOf[BTNSubmissionRepository]
  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled" -> false,
        "defaultDataExpireInDays" -> 28
      )
      .build()

  
  private val validPillar2Id = "XMPLR0000000000"
  private val validRequest = BTNRequest(
    accountingPeriodFrom = LocalDate.of(2024, 1, 1),
    accountingPeriodTo = LocalDate.of(2024, 12, 31)
  )

 
  private def submitBTN(pillar2Id: String, request: BTNRequest): HttpResponse = {
    val headers = Seq(
      "Content-Type" -> "application/json",
      "Authorization" -> "Bearer token",
      "X-Pillar2-Id" -> pillar2Id
    )
    
    httpClient
      .post(url"$baseUrl/RESTAdapter/PLR/below-threshold-notification")
      .transform(_.withHttpHeaders(headers: _*))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
  }

  override protected def prepareDatabase(): Unit = {
    repository.collection.drop().toFuture().futureValue
    repository.collection.createIndexes(repository.indexes).toFuture().futureValue
    ()
  }

  "BTN submission endpoint" should {
    "successfully save and retrieve BTN submissions" in {
      val response = submitBTN(validPillar2Id, validRequest)
      response.status shouldBe 201

      val submissions = repository.findByPillar2Id(validPillar2Id).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id shouldBe validPillar2Id
      submission.accountingPeriodFrom shouldBe validRequest.accountingPeriodFrom
      submission.accountingPeriodTo shouldBe validRequest.accountingPeriodTo
    }

    "allow multiple submissions for the same Pillar2 ID with different accounting periods" in {

      submitBTN(validPillar2Id, validRequest).status shouldBe 201

      val secondRequest = validRequest.copy(
        accountingPeriodFrom = LocalDate.of(2025, 1, 1),
        accountingPeriodTo = LocalDate.of(2025, 12, 31)
      )
      submitBTN(validPillar2Id, secondRequest).status shouldBe 201

      val submissions = repository.findByPillar2Id(validPillar2Id).futureValue
      submissions.size shouldBe 2
      submissions.map(_.accountingPeriodFrom) should contain theSameElementsAs Seq(
        LocalDate.of(2024, 1, 1),
        LocalDate.of(2025, 1, 1)
      )
    }

    "handle invalid requests appropriately" in {
    
      val headers = Seq(
        "Content-Type" -> "application/json",
        "Authorization" -> "Bearer token"
      )
      
      val responseWithoutId = httpClient
        .post(url"$baseUrl/RESTAdapter/PLR/below-threshold-notification")
        .transform(_.withHttpHeaders(headers: _*))
        .withBody(Json.toJson(validRequest))
        .execute[HttpResponse]
        .futureValue

      responseWithoutId.status shouldBe 422
      val json = Json.parse(responseWithoutId.body)
      (json \ "errors" \ "code").as[String] shouldBe "002"
      repository.findByPillar2Id(validPillar2Id).futureValue shouldBe empty
    }

    "handle server error cases correctly" in {
      val errorPillar2Id = "XEPLR0000000500"
      val response = submitBTN(errorPillar2Id, validRequest)
      
      response.status shouldBe 500
      val json = Json.parse(response.body)
      (json \ "error" \ "code").as[String] shouldBe "500"
      repository.findByPillar2Id(errorPillar2Id).futureValue shouldBe empty
    }
  }
} 