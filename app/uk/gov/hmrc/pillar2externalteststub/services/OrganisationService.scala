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

import uk.gov.hmrc.pillar2externalteststub.models.error.{OrganisationAlreadyExists, OrganisationNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{TestOrganisation, TestOrganisationWithId}
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationService @Inject() (
  repository:  OrganisationRepository
)(implicit ec: ExecutionContext) {

  def createOrganisation(pillar2Id: String, details: TestOrganisation): Future[TestOrganisationWithId] = {
    val organisationWithId = details.withPillar2Id(pillar2Id)
    repository.findByPillar2Id(pillar2Id).flatMap {
      case Right(Some(_)) =>
        Future.failed(OrganisationAlreadyExists(pillar2Id))
      case Right(None) =>
        repository.insert(organisationWithId).flatMap {
          case Right(_)    => Future.successful(organisationWithId)
          case Left(error) => Future.failed(error)
        }
      case Left(error) => Future.failed(error)
    }
  }

  def getOrganisation(pillar2Id: String): Future[TestOrganisationWithId] =
    repository
      .findByPillar2Id(pillar2Id)
      .flatMap {
        case Right(Some(org)) => Future.successful(org)
        case Right(None)      => Future.failed(OrganisationNotFound(pillar2Id))
        case Left(error)      => Future.failed(error)
      }

  def updateOrganisation(pillar2Id: String, organisation: TestOrganisation): Future[TestOrganisationWithId] = {
    val organisationWithId = organisation.withPillar2Id(pillar2Id)
    repository.findByPillar2Id(pillar2Id).flatMap {
      case Right(None) =>
        Future.failed(OrganisationNotFound(pillar2Id))
      case Right(Some(_)) =>
        repository.update(organisationWithId).flatMap {
          case Right(_)    => Future.successful(organisationWithId)
          case Left(error) => Future.failed(error)
        }
      case Left(error) => Future.failed(error)
    }
  }

  def deleteOrganisation(pillar2Id: String): Future[Unit] =
    repository.findByPillar2Id(pillar2Id).flatMap {
      case Right(None) =>
        Future.failed(OrganisationNotFound(pillar2Id))
      case Right(Some(_)) =>
        repository.delete(pillar2Id).flatMap {
          case Right(_)    => Future.successful(())
          case Left(error) => Future.failed(error)
        }
      case Left(error) => Future.failed(error)
    }
}
