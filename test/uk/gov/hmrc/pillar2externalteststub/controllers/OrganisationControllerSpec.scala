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
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrganisationControllerSpec extends AnyWordSpec with Matchers with MockitoSugar {

  private val mockService = mock[OrganisationService]
  private val cc          = Helpers.stubControllerComponents()
  private val controller  = new OrganisationController(cc, mockService)

  private val orgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.of(2024, 1, 1)
  )

  private val accountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31),
    duetDate = LocalDate.of(2024, 4, 6)
  )

  private val organisationDetails = OrganisationDetails(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    lastUpdated = java.time.Instant.parse("2024-01-01T00:00:00Z")
  )

  private val pillar2Id          = "TEST123"
  private val organisationWithId = organisationDetails.withPillar2Id(pillar2Id)

  "create" should {
    "return 201 when organisation is created successfully" in {
      when(mockService.createOrganisation(eqTo(pillar2Id), any[OrganisationDetails]))
        .thenReturn(Future.successful(Right(organisationWithId)))

      val result = controller.create(pillar2Id)(
        FakeRequest()
          .withBody(Json.toJson(organisationDetails))
      )
      status(result)        shouldBe Status.CREATED
      contentAsJson(result) shouldBe Json.toJson(organisationWithId)
    }

    "return 400 when request body is invalid" in {
      val result = controller.create(pillar2Id)(
        FakeRequest()
          .withBody(Json.obj())
      )
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 400 when Pillar2 ID is empty" in {
      val result = controller.create("")(FakeRequest().withBody(Json.toJson(organisationDetails)))
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 500 when service fails to create organisation" in {
      when(mockService.createOrganisation(eqTo(pillar2Id), any[OrganisationDetails]))
        .thenReturn(Future.successful(Left("Failed to create organisation")))

      val result = controller.create(pillar2Id)(
        FakeRequest()
          .withBody(Json.toJson(organisationDetails))
      )
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "get" should {
    "return 200 with organisation when found" in {
      when(mockService.getOrganisation(pillar2Id))
        .thenReturn(Future.successful(Some(organisationWithId)))

      val result = controller.get(pillar2Id)(FakeRequest())
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(organisationWithId)
    }

    "return 404 when organisation is not found" in {
      when(mockService.getOrganisation(pillar2Id))
        .thenReturn(Future.successful(None))

      val result = controller.get(pillar2Id)(FakeRequest())
      status(result) shouldBe Status.NOT_FOUND
    }
  }

  "update" should {
    "return 200 when organisation is updated successfully" in {
      when(mockService.updateOrganisation(eqTo(pillar2Id), any[OrganisationDetails]))
        .thenReturn(Future.successful(Right(organisationWithId)))

      val result = controller.update(pillar2Id)(FakeRequest().withBody(Json.toJson(organisationDetails)))
      status(result)        shouldBe Status.OK
      contentAsJson(result) shouldBe Json.toJson(organisationWithId)
    }

    "return 400 when request body is invalid" in {
      val result = controller.update(pillar2Id)(FakeRequest().withBody(Json.obj()))
      status(result) shouldBe Status.BAD_REQUEST
    }

    "return 500 when service fails to update organisation" in {
      when(mockService.updateOrganisation(eqTo(pillar2Id), any[OrganisationDetails]))
        .thenReturn(Future.successful(Left("Failed to update organisation")))

      val result = controller.update(pillar2Id)(FakeRequest().withBody(Json.toJson(organisationDetails)))
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }

  "delete" should {
    "return 204 when organisation is deleted successfully" in {
      when(mockService.deleteOrganisation(pillar2Id))
        .thenReturn(Future.successful(true))

      val result = controller.delete(pillar2Id)(FakeRequest())
      status(result) shouldBe Status.NO_CONTENT
    }

    "return 500 when service fails to delete organisation" in {
      when(mockService.deleteOrganisation(pillar2Id))
        .thenReturn(Future.successful(false))

      val result = controller.delete(pillar2Id)(FakeRequest())
      status(result) shouldBe Status.INTERNAL_SERVER_ERROR
    }
  }
}
