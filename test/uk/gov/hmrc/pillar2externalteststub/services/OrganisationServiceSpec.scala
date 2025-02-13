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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.models.error._
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
    endDate = LocalDate.of(2024, 12, 31)
  )

  private val organisationDetails = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    lastUpdated = java.time.Instant.parse("2024-01-01T00:00:00Z")
  )

  private val pillar2Id          = "TEST123"
  private val organisationWithId = organisationDetails.withPillar2Id(pillar2Id)

  "createOrganisation" should {
    "return organisation details when creation is successful" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(None))
      when(mockRepository.insert(eqTo(organisationWithId)))
        .thenReturn(Future.successful(true))

      val result = service.createOrganisation(pillar2Id, organisationDetails).futureValue
      result shouldBe organisationWithId
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, times(1)).insert(organisationWithId)
    }

    "fail with OrganisationAlreadyExists when organisation exists" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))

      whenReady(service.createOrganisation(pillar2Id, organisationDetails).failed) { exception =>
        exception                                                 shouldBe a[OrganisationAlreadyExists]
        exception.asInstanceOf[OrganisationAlreadyExists].code    shouldBe "ORGANISATION_EXISTS"
        exception.asInstanceOf[OrganisationAlreadyExists].message shouldBe s"Organisation with pillar2Id: $pillar2Id already exists"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, never).insert(any[TestOrganisationWithId])
    }

    "propagate DatabaseError from repository when database operation fails" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(None))
      when(mockRepository.insert(eqTo(organisationWithId)))
        .thenReturn(Future.failed(DatabaseError("Failed to create organisation: Database connection failed")))

      whenReady(service.createOrganisation(pillar2Id, organisationDetails).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to create organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, times(1)).insert(organisationWithId)
    }
  }

  "getOrganisation" should {
    "return organisation when found" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))

      val result = service.getOrganisation(pillar2Id).futureValue
      result shouldBe organisationWithId
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
    }

    "fail with OrganisationNotFound when not found" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(None))

      whenReady(service.getOrganisation(pillar2Id).failed) { exception =>
        exception                                            shouldBe a[OrganisationNotFound]
        exception.asInstanceOf[OrganisationNotFound].code    shouldBe "ORGANISATION_NOT_FOUND"
        exception.asInstanceOf[OrganisationNotFound].message shouldBe s"No organisation found with pillar2Id: $pillar2Id"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
    }

    "propagate DatabaseError from repository when database operation fails" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.failed(DatabaseError("Failed to find organisation: Database connection failed")))

      whenReady(service.getOrganisation(pillar2Id).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to find organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
    }
  }

  "updateOrganisation" should {
    "return updated organisation when update is successful" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.update(eqTo(organisationWithId)))
        .thenReturn(Future.successful(true))

      val result = service.updateOrganisation(pillar2Id, organisationDetails).futureValue
      result shouldBe organisationWithId
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, times(1)).update(organisationWithId)
    }

    "propagate DatabaseError from repository when update fails" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.update(eqTo(organisationWithId)))
        .thenReturn(Future.failed(DatabaseError("Failed to update organisation: Database connection failed")))

      whenReady(service.updateOrganisation(pillar2Id, organisationDetails).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to update organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, times(1)).update(organisationWithId)
    }

    "fail with OrganisationNotFound when organisation does not exist" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(None))

      whenReady(service.updateOrganisation(pillar2Id, organisationDetails).failed) { exception =>
        exception                                            shouldBe a[OrganisationNotFound]
        exception.asInstanceOf[OrganisationNotFound].code    shouldBe "ORGANISATION_NOT_FOUND"
        exception.asInstanceOf[OrganisationNotFound].message shouldBe s"No organisation found with pillar2Id: $pillar2Id"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, never).update(any[TestOrganisationWithId])
    }
  }

  "deleteOrganisation" should {
    "return unit when deletion is successful" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.delete(eqTo(pillar2Id)))
        .thenReturn(Future.successful(true))

      service.deleteOrganisation(pillar2Id).futureValue shouldBe (())
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, times(1)).delete(pillar2Id)
    }

    "fail with OrganisationNotFound when organisation does not exist" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(None))

      whenReady(service.deleteOrganisation(pillar2Id).failed) { exception =>
        exception                                            shouldBe a[OrganisationNotFound]
        exception.asInstanceOf[OrganisationNotFound].code    shouldBe "ORGANISATION_NOT_FOUND"
        exception.asInstanceOf[OrganisationNotFound].message shouldBe s"No organisation found with pillar2Id: $pillar2Id"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, never).delete(any[String])
    }

    "propagate DatabaseError from repository when database operation fails" in {
      when(mockRepository.findByPillar2Id(eqTo(pillar2Id)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.delete(eqTo(pillar2Id)))
        .thenReturn(Future.failed(DatabaseError("Failed to delete organisation: Database connection failed")))

      whenReady(service.deleteOrganisation(pillar2Id).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to delete organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(pillar2Id)
      verify(mockRepository, times(1)).delete(pillar2Id)
    }
  }
}
