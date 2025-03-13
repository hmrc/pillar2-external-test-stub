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

import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, inject}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.helpers.{ORNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.orn.mongo.ORNSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.ORNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import scala.concurrent.{ExecutionContext, Future}

class ORNISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[ORNSubmission]
    with BeforeAndAfterEach
    with ORNDataFixture
    with TestOrgDataFixture {

  override protected val databaseName: String = "test-orn-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: ORNSubmissionRepository = app.injector.instanceOf[ORNSubmissionRepository]
  implicit val ec:                   ExecutionContext        = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                   HeaderCarrier           = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"             -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled"         -> false,
        "defaultDataExpireInDays" -> 28
      )
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .build()

  private def submitORN(pillar2Id: String, request: ORNRequest): HttpResponse = {
    val headers = Seq(
      "Content-Type"  -> "application/json",
      "Authorization" -> "Bearer token",
      "X-Pillar2-Id"  -> pillar2Id
    )

    httpClient
      .post(url"$baseUrl/RESTAdapter/PLR/overseas-return-notification")
      .transform(_.withHttpHeaders(headers: _*))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue
  }

  private def amendORN(pillar2Id: String, request: ORNRequest): HttpResponse = {
    val headers = Seq(
      "Content-Type"  -> "application/json",
      "Authorization" -> "Bearer token",
      "X-Pillar2-Id"  -> pillar2Id
    )

    httpClient
      .put(url"$baseUrl/RESTAdapter/PLR/overseas-return-notification")
      .transform(_.withHttpHeaders(headers: _*))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue
  }

  private def getORN(pillar2Id: String, accountingPeriodFrom: String, accountingPeriodTo: String): HttpResponse = {
    val headers = Seq(
      "Content-Type"  -> "application/json",
      "Authorization" -> "Bearer token",
      "X-Pillar2-Id"  -> pillar2Id
    )

    httpClient
      .get(url"$baseUrl/RESTAdapter/PLR/overseas-return-notification?accountingPeriodFrom=$accountingPeriodFrom&accountingPeriodTo=$accountingPeriodTo")
      .transform(_.withHttpHeaders(headers: _*))
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

  "ORN submission endpoint" should {
    "successfully save and retrieve ORN submissions" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val response = submitORN(validPlrId, validORNRequest)
      response.status shouldBe 201

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id shouldBe validPlrId
      submission.accountingPeriodFrom shouldBe validORNRequest.accountingPeriodFrom
      submission.accountingPeriodTo shouldBe validORNRequest.accountingPeriodTo
    }

    "return 422 with tax obligation already fulfilled when submitting duplicate ORN" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First submission
      val firstResponse = submitORN(validPlrId, validORNRequest)
      firstResponse.status shouldBe 201

      // Second submission with same accounting period
      val secondResponse = submitORN(validPlrId, validORNRequest)
      secondResponse.status shouldBe 422
      val json = Json.parse(secondResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "044"
      (json \ "errors" \ "text").as[String] shouldBe "Tax obligation already fulfilled"
    }

    "allow submission for different accounting periods" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First submission
      val firstResponse = submitORN(validPlrId, validORNRequest)
      firstResponse.status shouldBe 201

      // Second submission with different accounting period
      val differentPeriodRequest = validORNRequest.copy(
        accountingPeriodFrom = validORNRequest.accountingPeriodFrom.plusYears(1),
        accountingPeriodTo = validORNRequest.accountingPeriodTo.plusYears(1)
      )
      val secondResponse = submitORN(validPlrId, differentPeriodRequest)
      secondResponse.status shouldBe 201

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 2
    }

    "successfully amend an existing ORN submission" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val submitResponse = submitORN(validPlrId, validORNRequest)
      submitResponse.status shouldBe 201

      val amendedRequest = validORNRequest.copy(
        reportingEntityName = "Updated Newco PLC"
      )
      val amendResponse = amendORN(validPlrId, amendedRequest)
      amendResponse.status shouldBe 200

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 2
      submissions.last.reportingEntityName shouldBe "Updated Newco PLC"
    }

    "return 422 when attempting to amend non-existent ORN" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val amendResponse = amendORN(validPlrId, validORNRequest)
      amendResponse.status shouldBe 422
      val json = Json.parse(amendResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions shouldBe empty
    }

    "handle invalid requests appropriately" in {
      val headers = Seq(
        "Content-Type"  -> "application/json",
        "Authorization" -> "Bearer token"
      )

      val responseWithoutId = httpClient
        .post(url"$baseUrl/RESTAdapter/PLR/overseas-return-notification")
        .transform(_.withHttpHeaders(headers: _*))
        .withBody(Json.toJson(validORNRequest))
        .execute[HttpResponse]
        .futureValue

      responseWithoutId.status shouldBe 422
      val json = Json.parse(responseWithoutId.body)
      (json \ "errors" \ "code").as[String] shouldBe "002"

      repository.findByPillar2Id(validPlrId).futureValue shouldBe empty
    }

    "handle server error cases correctly" in {
      val response = submitORN(serverErrorPlrId, validORNRequest)

      response.status shouldBe 500
      val json = Json.parse(response.body)
      (json \ "errors" \ "code").as[String] shouldBe "500"

      repository.findByPillar2Id(serverErrorPlrId).futureValue shouldBe empty
    }

    "handle non-existent organisation" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val response = submitORN(validPlrId, validORNRequest)
      response.status shouldBe 422
      val json = Json.parse(response.body)
      (json \ "errors" \ "code").as[String] shouldBe "007"
    }
  }

  "GET ORN endpoint" should {
    "successfully retrieve an existing ORN submission" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First create a submission
      val submitResponse = submitORN(validPlrId, validORNRequest)
      submitResponse.status shouldBe 201

      // Then retrieve it
      val getResponse = getORN(validPlrId, validORNRequest.accountingPeriodFrom.toString, validORNRequest.accountingPeriodTo.toString)
      getResponse.status shouldBe 200
      
      val submission = Json.parse(getResponse.body)
      (submission \ "success" \ "accountingPeriodFrom").as[String] shouldBe validORNRequest.accountingPeriodFrom.toString
      (submission \ "success" \ "accountingPeriodTo").as[String] shouldBe validORNRequest.accountingPeriodTo.toString
      (submission \ "success" \ "filedDateGIR").as[String] shouldBe validORNRequest.filedDateGIR.toString
      (submission \ "success" \ "countryGIR").as[String] shouldBe validORNRequest.countryGIR
      (submission \ "success" \ "reportingEntityName").as[String] shouldBe validORNRequest.reportingEntityName
      (submission \ "success" \ "TIN").as[String] shouldBe validORNRequest.TIN
      (submission \ "success" \ "issuingCountryTIN").as[String] shouldBe validORNRequest.issuingCountryTIN
      (submission \ "success" \ "processingDate").as[String] should not be empty
    }

    "return 422 when no submission exists for the given period" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val getResponse = getORN(validPlrId, "2025-01-01", "2025-12-31")
      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
    }

    "return 422 when dates are invalid" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val getResponse = getORN(validPlrId, "invalid-date", "2025-12-31")
      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
    }

    "return 422 when Pillar2 ID is missing" in {
      val headers = Seq(
        "Content-Type"  -> "application/json",
        "Authorization" -> "Bearer token"
      )

      val getResponse = httpClient
        .get(url"$baseUrl/RESTAdapter/PLR/overseas-return-notification?accountingPeriodFrom=2024-01-01&accountingPeriodTo=2024-12-31")
        .transform(_.withHttpHeaders(headers: _*))
        .execute[HttpResponse]
        .futureValue

      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "002"
    }

    "return 422 when organisation does not exist" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val getResponse = getORN(validPlrId, "2024-01-01", "2024-12-31")
      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "003"
    }

    "return 500 for server error PLR ID" in {
      val getResponse = getORN(serverErrorPlrId, "2024-01-01", "2024-12-31")
      getResponse.status shouldBe 500
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "500"
    }
  }
}

