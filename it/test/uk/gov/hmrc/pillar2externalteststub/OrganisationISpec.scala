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
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.StringContextOps
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository
import uk.gov.hmrc.pillar2externalteststub.models.response.StubErrorResponse

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class OrganisationISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[TestOrganisationWithId]
    with BeforeAndAfterEach {

  override protected val databaseName: String = "test-organisation-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl  = s"http://localhost:$port"
  override protected val repository = app.injector.instanceOf[OrganisationRepository]
  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"     -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled" -> false
      )
      .build()

  // Test organisation details to be used across tests
  private val testOrgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Integration Org",
    registrationDate = LocalDate.of(2024, 1, 1)
  )

  private val testAccountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31)
  )

  private val testOrganisationRequest = TestOrganisationRequest(
    orgDetails = testOrgDetails,
    accountingPeriod = testAccountingPeriod
  )

  // For simplicity, we use a fixed pillar2Id.
  private val pillar2Id = "XEPLR1234567890"

  // Helper method to extract organisation name from a JSON response.
  private def extractOrganisationName(json: JsValue): String =
    (json \ "organisation" \ "orgDetails" \ "organisationName").as[String]

  // Helper methods for CRUD operations.
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

  // Ensure the repository is clean before every test.
  override def beforeEach(): Unit = {
    super.beforeEach()
    repository.delete(pillar2Id).futureValue
    ()
  }

  "Organisation endpoints" should {

    "when organisation exists" should {

      "support the full CRUD lifecycle" in {
        // Create an organisation.
        val createResponse = createOrganisation(pillar2Id, testOrganisationRequest)
        createResponse.status shouldBe 201
        extractOrganisationName(Json.parse(createResponse.body)) shouldBe "Test Integration Org"

        // Verify the organisation exists in MongoDB.
        val storedOrg = repository.findByPillar2Id(pillar2Id).futureValue
        storedOrg.isDefined shouldBe true
        storedOrg.get.organisation.orgDetails.organisationName shouldBe "Test Integration Org"

        // Retrieve the organisation.
        val getResponse = getOrganisation(pillar2Id)
        getResponse.status shouldBe 200
        extractOrganisationName(Json.parse(getResponse.body)) shouldBe "Test Integration Org"

        // Update organisation (change organisation name).
        val updatedRequest = testOrganisationRequest.copy(
          orgDetails = testOrgDetails.copy(organisationName = "Updated Integration Org")
        )
        // Update the organisation.
        val updateResponse = updateOrganisation(pillar2Id, updatedRequest)
        updateResponse.status shouldBe 200
        extractOrganisationName(Json.parse(updateResponse.body)) shouldBe "Updated Integration Org"

        // Retrieve the updated organisation.
        val getUpdatedResponse = getOrganisation(pillar2Id)
        getUpdatedResponse.status shouldBe 200
        extractOrganisationName(Json.parse(getUpdatedResponse.body)) shouldBe "Updated Integration Org"

        // Delete the organisation.
        val deleteResponse = deleteOrganisation(pillar2Id)
        deleteResponse.status shouldBe 204

        // Verify deletion in the repository.
        repository.findByPillar2Id(pillar2Id).futureValue shouldBe None
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
        // Create the organisation for the first time.
        val firstResponse = createOrganisation(pillar2Id, testOrganisationRequest)
        firstResponse.status shouldBe 201

        // Attempt duplicate creation.
        val duplicateResponse = createOrganisation(pillar2Id, testOrganisationRequest)
        duplicateResponse.status shouldBe 409

        // Verify duplicate error details.
        val error = Json.parse(duplicateResponse.body).as[StubErrorResponse]
        error.code shouldBe "ORGANISATION_EXISTS"
        error.message shouldBe s"Organisation with pillar2Id: $pillar2Id already exists"
      }

      "return 400 for invalid JSON payload on creation" in {
        // Create an invalid JSON payload.
        val invalidJson = Json.obj("invalid" -> "request")
        // POST the invalid payload.
        val response = httpClient
          .post(url"$baseUrl/pillar2/test/organisation/$pillar2Id")
          .withBody(invalidJson)
          .execute[HttpResponse]
          .futureValue

        // Check the error response.
        response.status shouldBe 400
        val error = Json.parse(response.body).as[StubErrorResponse]
        error.code shouldBe "INVALID_JSON"
      }
    }
  }
} 