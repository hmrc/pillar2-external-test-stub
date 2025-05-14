/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.pillar2externalteststub.helpers.{GIRDataFixture, ObligationsAndSubmissionsDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.mongo.GIRSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.{GIRSubmissionRepository, ObligationsAndSubmissionsRepository}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import scala.concurrent.{ExecutionContext, Future}

class GIRISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[GIRSubmission]
    with BeforeAndAfterEach
    with GIRDataFixture
    with TestOrgDataFixture
    with ObligationsAndSubmissionsDataFixture {

  override protected val databaseName: String = "test-gir-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: GIRSubmissionRepository             = app.injector.instanceOf[GIRSubmissionRepository]
  private val oasRepository:         ObligationsAndSubmissionsRepository = app.injector.instanceOf[ObligationsAndSubmissionsRepository]
  implicit val ec:                   ExecutionContext                  = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                   HeaderCarrier                     = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"             -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled"         -> false,
        "defaultDataExpireInDays" -> 28
      )
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .build()

  private def submitGIR(pillar2Id: String, request: GIRRequest): HttpResponse = {
    val headers = Seq(
      "Content-Type"  -> "application/json",
      "Authorization" -> "Bearer token",
      "X-Pillar2-Id"  -> pillar2Id
    )

    httpClient
      .post(url"$baseUrl/pillar2/test/globe-information-return")
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
    oasRepository.collection.drop().toFuture().futureValue
    oasRepository.collection.createIndexes(oasRepository.indexes).toFuture().futureValue
    ()
  }

  "GIR submission endpoint" should {
    "successfully save and retrieve GIR submissions" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val response = submitGIR(validPlrId, validGIRRequest)
      response.status shouldBe 201

      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id            shouldBe validPlrId
      submission.accountingPeriodFrom shouldBe validGIRRequest.accountingPeriodFrom
      submission.accountingPeriodTo   shouldBe validGIRRequest.accountingPeriodTo
    }

    "fail with TaxObligationAlreadyFulfilled when submitting twice in a row" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First submission should succeed
      val firstResponse = submitGIR(validPlrId, validGIRRequest)
      firstResponse.status shouldBe 201

      // Second submission should fail with TaxObligationAlreadyFulfilled
      val secondResponse = submitGIR(validPlrId, validGIRRequest)
      secondResponse.status shouldBe 422 //This should be TaxObligationAlreadyFulfilled - 044

      // Verify the error code
      val json = Json.parse(secondResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "044"
      (json \ "errors" \ "text").as[String] shouldBe "Tax obligation already fulfilled"

      // Verify only one submission exists
      val submissions = repository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
    }

    "handle invalid requests appropriately (missing Pillar2 ID)" in {
      val headers = Seq(
        "Content-Type"  -> "application/json",
        "Authorization" -> "Bearer token"
      )

      val responseWithoutId = httpClient
        .post(url"$baseUrl/pillar2/test/globe-information-return")
        .transform(_.withHttpHeaders(headers: _*))
        .withBody(Json.toJson(validGIRRequest))
        .execute[HttpResponse]
        .futureValue

      responseWithoutId.status shouldBe 422 // This should be IdMissingOrInvalid - 089
      val json = Json.parse(responseWithoutId.body)
      (json \ "errors" \ "code").as[String] shouldBe "089"

      repository.findByPillar2Id(validPlrId).futureValue shouldBe empty
    }

    "handle server error cases correctly" in {
      val response = submitGIR(serverErrorPlrId, validGIRRequest)

      response.status shouldBe 500
      val json = Json.parse(response.body)
      (json \ "error" \ "code").as[String] shouldBe "500"

      repository.findByPillar2Id(serverErrorPlrId).futureValue shouldBe empty
    }
  }
}

