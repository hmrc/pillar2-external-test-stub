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
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class OrganisationServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BeforeAndAfterEach with TestOrgDataFixture {

  private val mockRepository = mock[OrganisationRepository]
  private val service        = new OrganisationService(mockRepository)

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockRepository)
  }

  "createOrganisation" should {
    "return organisation details when creation is successful" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(None))
      when(mockRepository.insert(eqTo(organisationWithId)))
        .thenReturn(Future.successful(true))

      val result = service.createOrganisation(validPlrId, organisationDetails).futureValue
      result shouldBe organisationWithId
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, times(1)).insert(organisationWithId)
    }

    "fail with OrganisationAlreadyExists when organisation exists" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(Some(organisationWithId)))

      whenReady(service.createOrganisation(validPlrId, organisationDetails).failed) { exception =>
        exception                                                 shouldBe a[OrganisationAlreadyExists]
        exception.asInstanceOf[OrganisationAlreadyExists].code    shouldBe "ORGANISATION_EXISTS"
        exception.asInstanceOf[OrganisationAlreadyExists].message shouldBe s"Organisation with pillar2Id: $validPlrId already exists"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, never).insert(any[TestOrganisationWithId])
    }

    "propagate DatabaseError from repository when database operation fails" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(None))
      when(mockRepository.insert(eqTo(organisationWithId)))
        .thenReturn(Future.failed(DatabaseError("Failed to create organisation: Database connection failed")))

      whenReady(service.createOrganisation(validPlrId, organisationDetails).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to create organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, times(1)).insert(organisationWithId)
    }
  }

  "getOrganisation" should {
    "return organisation when found" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(Some(organisationWithId)))

      val result = service.getOrganisation(validPlrId).futureValue
      result shouldBe organisationWithId
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
    }

    "fail with OrganisationNotFound when not found" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(None))

      whenReady(service.getOrganisation(validPlrId).failed) { exception =>
        exception                                            shouldBe a[OrganisationNotFound]
        exception.asInstanceOf[OrganisationNotFound].code    shouldBe "ORGANISATION_NOT_FOUND"
        exception.asInstanceOf[OrganisationNotFound].message shouldBe s"No organisation found with pillar2Id: $validPlrId"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
    }

    "propagate DatabaseError from repository when database operation fails" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.failed(DatabaseError("Failed to find organisation: Database connection failed")))

      whenReady(service.getOrganisation(validPlrId).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to find organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
    }
  }

  "updateOrganisation" should {
    "return updated organisation when update is successful" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.update(eqTo(organisationWithId)))
        .thenReturn(Future.successful(true))

      val result = service.updateOrganisation(validPlrId, organisationDetails).futureValue
      result shouldBe organisationWithId
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, times(1)).update(organisationWithId)
    }

    "propagate DatabaseError from repository when update fails" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.update(eqTo(organisationWithId)))
        .thenReturn(Future.failed(DatabaseError("Failed to update organisation: Database connection failed")))

      whenReady(service.updateOrganisation(validPlrId, organisationDetails).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to update organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, times(1)).update(organisationWithId)
    }

    "fail with OrganisationNotFound when organisation does not exist" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(None))

      whenReady(service.updateOrganisation(validPlrId, organisationDetails).failed) { exception =>
        exception                                            shouldBe a[OrganisationNotFound]
        exception.asInstanceOf[OrganisationNotFound].code    shouldBe "ORGANISATION_NOT_FOUND"
        exception.asInstanceOf[OrganisationNotFound].message shouldBe s"No organisation found with pillar2Id: $validPlrId"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, never).update(any[TestOrganisationWithId])
    }
  }

  "deleteOrganisation" should {
    "return unit when deletion is successful" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.delete(eqTo(validPlrId)))
        .thenReturn(Future.successful(true))

      service.deleteOrganisation(validPlrId).futureValue shouldBe (())
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, times(1)).delete(validPlrId)
    }

    "fail with OrganisationNotFound when organisation does not exist" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(None))

      whenReady(service.deleteOrganisation(validPlrId).failed) { exception =>
        exception                                            shouldBe a[OrganisationNotFound]
        exception.asInstanceOf[OrganisationNotFound].code    shouldBe "ORGANISATION_NOT_FOUND"
        exception.asInstanceOf[OrganisationNotFound].message shouldBe s"No organisation found with pillar2Id: $validPlrId"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, never).delete(any[String])
    }

    "propagate DatabaseError from repository when database operation fails" in {
      when(mockRepository.findByPillar2Id(eqTo(validPlrId)))
        .thenReturn(Future.successful(Some(organisationWithId)))
      when(mockRepository.delete(eqTo(validPlrId)))
        .thenReturn(Future.failed(DatabaseError("Failed to delete organisation: Database connection failed")))

      whenReady(service.deleteOrganisation(validPlrId).failed) { exception =>
        exception                                     shouldBe a[DatabaseError]
        exception.asInstanceOf[DatabaseError].code    shouldBe "DATABASE_ERROR"
        exception.asInstanceOf[DatabaseError].message shouldBe "Failed to delete organisation: Database connection failed"
      }
      verify(mockRepository, times(1)).findByPillar2Id(validPlrId)
      verify(mockRepository, times(1)).delete(validPlrId)
    }
  }
}
