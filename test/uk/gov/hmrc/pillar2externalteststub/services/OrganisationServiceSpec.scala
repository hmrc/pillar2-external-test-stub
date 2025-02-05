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

package uk.gov.hmrc.pillar2externalteststub.services

import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrganisationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach {

  private val mockRepository = mock[OrganisationRepository]
  private val service        = new OrganisationService(mockRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
  }

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

  "createorganisation" should {
    "return Right with organisation details when creation is successful" in {
      when(mockRepository.insert(eqTo(organisationWithId))).thenReturn(Future.successful(true))

      val result = service.createOrganisation(pillar2Id, organisationDetails).futureValue
      result shouldBe Right(organisationWithId)
      verify(mockRepository, times(1)).insert(organisationWithId)
    }

    "return Left with error message when creation fails" in {
      when(mockRepository.insert(eqTo(organisationWithId))).thenReturn(Future.successful(false))

      val result = service.createOrganisation(pillar2Id, organisationDetails).futureValue
      result shouldBe Left("Failed to create organisation")
      verify(mockRepository, times(1)).insert(organisationWithId)
    }
  }

  "getorganisation" should {
    "return Some(organisation) when found" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))

      val result = service.getOrganisation(pillar2Id).futureValue
      result shouldBe Some(organisationWithId)
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
    }

    "return None when not found" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(None))

      val result = service.getOrganisation(pillar2Id).futureValue
      result shouldBe None
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
    }
  }

  "updateorganisation" should {
    "return Right with updated organisation when update is successful" in {
      when(mockRepository.update(eqTo(organisationWithId))).thenReturn(Future.successful(true))

      val result = service.updateOrganisation(pillar2Id, organisationDetails).futureValue
      result shouldBe Right(organisationWithId)
      verify(mockRepository, times(1)).update(organisationWithId)
    }

    "return Left with error message when update fails" in {
      when(mockRepository.update(eqTo(organisationWithId))).thenReturn(Future.successful(false))

      val result = service.updateOrganisation(pillar2Id, organisationDetails).futureValue
      result shouldBe Left("Failed to update organisation")
      verify(mockRepository, times(1)).update(organisationWithId)
    }
  }

  "deleteorganisation" should {
    "return true when deletion is successful" in {
      when(mockRepository.delete(eqTo(pillar2Id))).thenReturn(Future.successful(true))

      val result = service.deleteOrganisation(pillar2Id).futureValue
      result shouldBe true
      verify(mockRepository, times(1)).delete(pillar2Id)
    }

    "return false when deletion fails" in {
      when(mockRepository.delete(eqTo(pillar2Id))).thenReturn(Future.successful(false))

      val result = service.deleteOrganisation(pillar2Id).futureValue
      result shouldBe false
      verify(mockRepository, times(1)).delete(pillar2Id)
    }
  }
}
