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

package uk.gov.hmrc.pillar2externalteststub.controllers

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrganisationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar with TestOrgDataFixture {

  private val cc         = Helpers.stubControllerComponents()
  private val controller = new OrganisationController(cc, mockOrgService)

  "create" should {
    "return 201 when organisation is created successfully" in {
      when(mockOrgService.createOrganisation(eqTo(validPlrId), any[TestOrganisation]))
        .thenReturn(Future.successful(organisationWithId))

      val result = controller.create(validPlrId)(
        FakeRequest("POST", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      status(result)        shouldBe Status.CREATED
      contentAsJson(result) shouldBe Json.toJson(organisationWithId)
    }

    "return 400 when request body is invalid" in {
      val result = controller.create(validPlrId)(
        FakeRequest("POST", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.obj())
      )
      result shouldFailWith InvalidJson
    }

    "return 400 when Pillar2 ID is empty" in {
      val result = controller.create("")(
        FakeRequest("POST", "/pillar2/test/organisation/")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      result shouldFailWith EmptyRequestBody
    }

    "return 409 when organisation already exists" in {
      when(mockOrgService.createOrganisation(eqTo(validPlrId), any[TestOrganisation]))
        .thenReturn(Future.failed(OrganisationAlreadyExists(validPlrId)))

      val result = controller.create(validPlrId)(
        FakeRequest("POST", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      result shouldFailWith OrganisationAlreadyExists(validPlrId)
    }

    "return 500 when database operation fails" in {
      when(mockOrgService.createOrganisation(eqTo(validPlrId), any[TestOrganisation]))
        .thenReturn(Future.failed(DatabaseError("Failed to create organisation: Database connection failed")))

      val result = controller.create(validPlrId)(
        FakeRequest("POST", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      result shouldFailWith DatabaseError("Failed to create organisation: Database connection failed")
    }
  }

  "get" should {
    "return 200 with organisation when found" in {
      when(mockOrgService.getOrganisation(validPlrId))
        .thenReturn(Future.successful(organisationWithId))

      val result = controller.get(validPlrId)(FakeRequest("GET", s"/pillar2/test/organisation/$validPlrId"))
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(organisationWithId)
    }

    "return 404 when organisation is not found" in {
      when(mockOrgService.getOrganisation(validPlrId))
        .thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val result = controller.get(validPlrId)(FakeRequest("GET", s"/pillar2/test/organisation/$validPlrId"))
      result shouldFailWith OrganisationNotFound(validPlrId)
    }

    "return 500 when database operation fails" in {
      when(mockOrgService.getOrganisation(validPlrId))
        .thenReturn(Future.failed(DatabaseError("Failed to find organisation: Database connection failed")))

      val result = controller.get(validPlrId)(FakeRequest("GET", s"/pillar2/test/organisation/$validPlrId"))
      result shouldFailWith DatabaseError("Failed to find organisation: Database connection failed")
    }
  }

  "update" should {
    "return 200 when organisation is updated successfully" in {
      when(mockOrgService.updateOrganisation(eqTo(validPlrId), any[TestOrganisation]))
        .thenReturn(Future.successful(organisationWithId))

      val result = controller.update(validPlrId)(
        FakeRequest("PUT", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(organisationWithId)
    }

    "return 400 when request body is invalid" in {
      val result = controller.update(validPlrId)(
        FakeRequest("PUT", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.obj())
      )
      result shouldFailWith InvalidJson
    }

    "return 404 when organisation does not exist" in {
      when(mockOrgService.updateOrganisation(eqTo(validPlrId), any[TestOrganisation]))
        .thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val result = controller.update(validPlrId)(
        FakeRequest("PUT", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      result shouldFailWith OrganisationNotFound(validPlrId)
    }

    "return 500 when database operation fails" in {
      when(mockOrgService.updateOrganisation(eqTo(validPlrId), any[TestOrganisation]))
        .thenReturn(Future.failed(DatabaseError("Failed to update organisation: Database connection failed")))

      val result = controller.update(validPlrId)(
        FakeRequest("PUT", s"/pillar2/test/organisation/$validPlrId")
          .withBody(Json.toJson(TestOrganisationRequest(orgDetails, accountingPeriod)))
      )
      result shouldFailWith DatabaseError("Failed to update organisation: Database connection failed")
    }
  }

  "delete" should {
    "return 204 when organisation is deleted successfully" in {
      when(mockOrgService.deleteOrganisation(validPlrId))
        .thenReturn(Future.successful(()))

      val result = controller.delete(validPlrId)(FakeRequest("DELETE", s"/pillar2/test/organisation/$validPlrId"))
      status(result) shouldBe Status.NO_CONTENT
    }

    "return 404 when organisation is not found" in {
      when(mockOrgService.deleteOrganisation(validPlrId))
        .thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val result = controller.delete(validPlrId)(FakeRequest("DELETE", s"/pillar2/test/organisation/$validPlrId"))
      result shouldFailWith OrganisationNotFound(validPlrId)
    }

    "return 500 when database operation fails" in {
      when(mockOrgService.deleteOrganisation(validPlrId))
        .thenReturn(Future.failed(DatabaseError("Failed to delete organisation: Database connection failed")))

      val result = controller.delete(validPlrId)(FakeRequest("DELETE", s"/pillar2/test/organisation/$validPlrId"))
      result shouldFailWith DatabaseError("Failed to delete organisation: Database connection failed")
    }
  }
}
