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
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, ObligationsAndSubmissionsDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.btn.mongo.BTNSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.BTNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import uk.gov.hmrc.pillar2externalteststub.repositories.ObligationsAndSubmissionsRepository

class BTNISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[BTNSubmission]
    with BeforeAndAfterEach
    with BTNDataFixture
    with TestOrgDataFixture
    with ObligationsAndSubmissionsDataFixture {

  override protected val databaseName: String = "test-btn-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: BTNSubmissionRepository             = app.injector.instanceOf[BTNSubmissionRepository]
  private val oasRepository:         ObligationsAndSubmissionsRepository = app.injector.instanceOf[ObligationsAndSubmissionsRepository]
  implicit val ec:                   ExecutionContext                    = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                   HeaderCarrier                       = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"             -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled"         -> false,
        "defaultDataExpireInDays" -> 28
      )
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .build()

  private def submitBTN(pillar2Id: String, request: BTNRequest): HttpResponse =
    httpClient
      .post(url"$baseUrl/RESTAdapter/plr/below-threshold-notification")
      .transform(_.withHttpHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
  }

  override protected def prepareDatabase(): Unit = {
    repository.collection.drop().toFuture().futureValue
    repository.collection.createIndexes(repository.indexes).toFuture().futureValue
    oasRepository.collection.drop().toFuture().futureValue
    oasRepository.collection.createIndexes(oasRepository.indexes).toFuture().futureValue
    ()
  }

  "BTN submission endpoint" should {
    "successfully save and retrieve BTN submissions" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
      when(mockOrgService.makeOrganisatonInactive(eqTo(validPlrId))).thenReturn(Future.successful(()))

      val response = submitBTN(validPlrId, validBTNRequest)
      response.status shouldBe 201

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id            shouldBe validPlrId
      submission.accountingPeriodFrom shouldBe validBTNRequest.accountingPeriodFrom
      submission.accountingPeriodTo   shouldBe validBTNRequest.accountingPeriodTo
    }

    "fail with TaxObligationAlreadyFulfilled when submitting twice in a row" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
      when(mockOrgService.makeOrganisatonInactive(eqTo(validPlrId))).thenReturn(Future.successful(()))

      // First submission should succeed
      val firstResponse = submitBTN(validPlrId, validBTNRequest)
      firstResponse.status shouldBe 201

      // Second submission should fail with TaxObligationAlreadyFulfilled
      val secondResponse = submitBTN(validPlrId, validBTNRequest)
      secondResponse.status shouldBe 422

      // Verify the error code
      val json = Json.parse(secondResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "044"
      (json \ "errors" \ "text").as[String] shouldBe "Tax obligation already fulfilled"

      // Verify only one submission exists
      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
    }

    "support only one accountingPeriod per Pillar2 ID" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))
      when(mockOrgService.makeOrganisatonInactive(eqTo(validPlrId))).thenReturn(Future.successful(()))
      
      submitBTN(validPlrId, validBTNRequest).status shouldBe 201

      val secondRequest = validBTNRequest.copy(
        accountingPeriodFrom = LocalDate.of(2025, 1, 1),
        accountingPeriodTo = LocalDate.of(2025, 12, 31)
      )
      submitBTN(validPlrId, secondRequest).status shouldBe 422

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
    }

    "handle invalid requests appropriately" in {
      val responseWithoutId = httpClient
        .post(url"$baseUrl/RESTAdapter/plr/below-threshold-notification")
        .transform(_.withHttpHeaders(hipHeaders: _*))
        .withBody(Json.toJson(validBTNRequest))
        .execute[HttpResponse]
        .futureValue

      responseWithoutId.status shouldBe 422
      val json = Json.parse(responseWithoutId.body)
      (json \ "errors" \ "code").as[String] shouldBe "089"

      repository.findByPillar2Id(validPlrId).futureValue shouldBe empty
    }

    "handle server error cases correctly" in {
      val response = submitBTN(serverErrorPlrId, validBTNRequest)

      response.status shouldBe 500
      val json = Json.parse(response.body)
      (json \ "error" \ "code").as[String] shouldBe "500"

      repository.findByPillar2Id(serverErrorPlrId).futureValue shouldBe empty
    }
  }
}
