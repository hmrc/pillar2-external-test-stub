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
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.WSClient
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class OrganisationISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[TestOrganisationWithId] {

  override protected val databaseName: String = "test-organisation-integration"

  private val wsClient = app.injector.instanceOf[WSClient]
  private val baseUrl  = s"http://localhost:$port"
  override protected val repository = app.injector.instanceOf[OrganisationRepository]
  implicit val ec: ExecutionContext   = app.injector.instanceOf[ExecutionContext]

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"      -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled"  -> false
      )
      .build()

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

  private val pillar2Id = "XEPLR1234567890"

  // Common JSON headers
  private def jsonHeaders = Seq("Content-Type" -> "application/json")

  // Helper to extract the organisation name from the response JSON
  private def extractOrganisationName(json: JsValue): String =
    (json \ "organisation" \ "orgDetails" \ "organisationName").as[String]

  "Organisation endpoints" should {

    "support the full CRUD lifecycle" in {
      // Create organisation
      val createResponse = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .withHttpHeaders(jsonHeaders: _*)
        .post(Json.toJson(testOrganisationRequest))
        .futureValue

      createResponse.status shouldBe 201
      extractOrganisationName(createResponse.json) shouldBe "Test Integration Org"

      // Verify existence in MongoDB
      val storedOrg = repository.findByPillar2Id(pillar2Id).futureValue
      storedOrg.isDefined shouldBe true
      storedOrg.get.organisation.orgDetails.organisationName shouldBe "Test Integration Org"

      // Get organisation
      val getResponse = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .get()
        .futureValue

      getResponse.status shouldBe 200
      extractOrganisationName(getResponse.json) shouldBe "Test Integration Org"

      // Update organisation
      val updatedRequest = testOrganisationRequest.copy(
        orgDetails = testOrgDetails.copy(organisationName = "Updated Integration Org")
      )
      val updateResponse = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .withHttpHeaders(jsonHeaders: _*)
        .put(Json.toJson(updatedRequest))
        .futureValue

      updateResponse.status shouldBe 200
      extractOrganisationName(updateResponse.json) shouldBe "Updated Integration Org"

      // Delete organisation
      val deleteResponse = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .delete()
        .futureValue

      deleteResponse.status shouldBe 204

      // Verify deletion in MongoDB
      repository.findByPillar2Id(pillar2Id).futureValue shouldBe None
    }

    "handle duplicate organisation creation with meaningful errors" in {
      // Cleanup before test to ensure the organisation does not exist
      repository.delete(pillar2Id).futureValue

      // First creation
      wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .withHttpHeaders(jsonHeaders: _*)
        .post(Json.toJson(testOrganisationRequest))
        .futureValue

      // Attempt duplicate creation
      val duplicateResponse = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .withHttpHeaders(jsonHeaders: _*)
        .post(Json.toJson(testOrganisationRequest))
        .futureValue

      duplicateResponse.status shouldBe 409
      (duplicateResponse.json \ "code").as[String] shouldBe "ORGANISATION_EXISTS"
      (duplicateResponse.json \ "message").as[String] shouldBe s"Organisation with pillar2Id: $pillar2Id already exists"
    }

    "return 400 for invalid JSON payload on creation" in {
      val invalidJson: JsValue = Json.obj("invalid" -> "request")
      val response = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/$pillar2Id")
        .withHttpHeaders(jsonHeaders: _*)
        .post(invalidJson)
        .futureValue

      response.status shouldBe 400
      (response.json \ "code").as[String] shouldBe "INVALID_JSON"
    }

    "return 404 when retrieving a non-existent organisation" in {
      val response = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/NONEXISTENT")
        .get()
        .futureValue

      response.status shouldBe 404
      (response.json \ "code").as[String] shouldBe "ORGANISATION_NOT_FOUND"
    }

    "return 404 when updating a non-existent organisation" in {
      val response = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/NONEXISTENT")
        .withHttpHeaders(jsonHeaders: _*)
        .put(Json.toJson(testOrganisationRequest))
        .futureValue

      response.status shouldBe 404
      (response.json \ "code").as[String] shouldBe "ORGANISATION_NOT_FOUND"
    }

    "return 404 when deleting a non-existent organisation" in {
      val response = wsClient
        .url(s"$baseUrl/pillar2/test/organisation/NONEXISTENT")
        .delete()
        .futureValue

      response.status shouldBe 404
      (response.json \ "code").as[String] shouldBe "ORGANISATION_NOT_FOUND"
    }
  }
} 