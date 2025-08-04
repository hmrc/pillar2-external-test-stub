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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.response.StubErrorResponse
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class OrganisationISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[TestOrganisationWithId]
    with BeforeAndAfterEach
    with TestOrgDataFixture {

  override protected val databaseName: String = "test-organisation-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  override protected val repository: OrganisationRepository = app.injector.instanceOf[OrganisationRepository]
  implicit val ec:                   ExecutionContext       = app.injector.instanceOf[ExecutionContext]
  implicit val hc:                   HeaderCarrier          = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"     -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled" -> false
      )
      .build()

  private val testAccountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31),
    None
  )

  private val testOrganisationRequest = TestOrganisationRequest(
    orgDetails = orgDetails,
    accountingPeriod = testAccountingPeriod
  )

  private def extractOrganisationName(json: JsValue): String =
    (json \ "organisation" \ "orgDetails" \ "organisationName").as[String]

  private def createOrganisation(id: String, request: TestOrganisationRequest): HttpResponse =
    httpClient
      .post(url"$baseUrl/pillar2/test/organisation/$id")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue

  private def getOrganisation(id: String): HttpResponse =
    httpClient
      .get(url"$baseUrl/pillar2/test/organisation/$id")
      .execute[HttpResponse]
      .futureValue

  private def updateOrganisation(id: String, request: TestOrganisationRequest): HttpResponse =
    httpClient
      .put(url"$baseUrl/pillar2/test/organisation/$id")
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue

  private def deleteOrganisation(id: String): HttpResponse =
    httpClient
      .delete(url"$baseUrl/pillar2/test/organisation/$id")
      .execute[HttpResponse]
      .futureValue

  override def beforeEach(): Unit = {
    super.beforeEach()
    repository.delete(validPlrId).futureValue
    ()
  }

  "Organisation endpoints" should {

    "when organisation exists" should {

      "support the full CRUD lifecycle" in {
        val createResponse = createOrganisation(validPlrId, testOrganisationRequest)
        createResponse.status                                    shouldBe 201
        extractOrganisationName(Json.parse(createResponse.body)) shouldBe "Test Org"

        val storedOrg = repository.findByPillar2Id(validPlrId).futureValue
        storedOrg.isDefined                                    shouldBe true
        storedOrg.get.organisation.orgDetails.organisationName shouldBe "Test Org"

        val getResponse = getOrganisation(validPlrId)
        getResponse.status                                    shouldBe 200
        extractOrganisationName(Json.parse(getResponse.body)) shouldBe "Test Org"

        val updatedRequest = testOrganisationRequest.copy(
          orgDetails = orgDetails.copy(organisationName = "Updated Integration Org")
        )
        val updateResponse = updateOrganisation(validPlrId, updatedRequest)
        updateResponse.status                                    shouldBe 200
        extractOrganisationName(Json.parse(updateResponse.body)) shouldBe "Updated Integration Org"

        val getUpdatedResponse = getOrganisation(validPlrId)
        getUpdatedResponse.status                                    shouldBe 200
        extractOrganisationName(Json.parse(getUpdatedResponse.body)) shouldBe "Updated Integration Org"

        val deleteResponse = deleteOrganisation(validPlrId)
        deleteResponse.status shouldBe 204

        repository.findByPillar2Id(validPlrId).futureValue shouldBe None
      }
    }

    "when organisation does not exist" should {

      "return 404 when retrieving a non-existent organisation" in {
        val response = getOrganisation("NONEXISTENT")
        response.status shouldBe 404
        val error = Json.parse(response.body).as[StubErrorResponse]
        error.code shouldBe "ORGANISATION_NOT_FOUND"
      }

      "return 404 when updating a non-existent organisation" in {
        val response = updateOrganisation("NONEXISTENT", testOrganisationRequest)
        response.status shouldBe 404
        val error = Json.parse(response.body).as[StubErrorResponse]
        error.code shouldBe "ORGANISATION_NOT_FOUND"
      }

      "return 404 when deleting a non-existent organisation" in {
        val response = deleteOrganisation("NONEXISTENT")
        response.status shouldBe 404
        val error = Json.parse(response.body).as[StubErrorResponse]
        error.code shouldBe "ORGANISATION_NOT_FOUND"
      }
    }

    "error handling" should {

      "handle duplicate organisation creation gracefully" in {
        val firstResponse = createOrganisation(validPlrId, testOrganisationRequest)
        firstResponse.status shouldBe 201

        val duplicateResponse = createOrganisation(validPlrId, testOrganisationRequest)
        duplicateResponse.status shouldBe 409

        val error = Json.parse(duplicateResponse.body).as[StubErrorResponse]
        error.code    shouldBe "ORGANISATION_EXISTS"
        error.message shouldBe s"Organisation with pillar2Id: $validPlrId already exists"
      }

      "return 400 for invalid JSON payload on creation" in {
        val invalidJson = Json.obj("invalid" -> "request")
        val response = httpClient
          .post(url"$baseUrl/pillar2/test/organisation/$validPlrId")
          .withBody(invalidJson)
          .execute[HttpResponse]
          .futureValue

        response.status shouldBe 400
        val error = Json.parse(response.body).as[StubErrorResponse]
        error.code shouldBe "INVALID_JSON"
      }
    }
  }
}
