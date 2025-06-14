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
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{AccountStatus, TestOrganisation, TestOrganisationWithId}
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
      case Some(_) =>
        Future.failed(OrganisationAlreadyExists(pillar2Id))
      case None =>
        repository.insert(organisationWithId).map(_ => organisationWithId)
    }
  }

  def getOrganisation(pillar2Id: String): Future[TestOrganisationWithId] =
    repository
      .findByPillar2Id(pillar2Id)
      .flatMap {
        case Some(org) => Future.successful(org)
        case None      => Future.failed(OrganisationNotFound(pillar2Id))
      }

  def updateOrganisation(pillar2Id: String, organisation: TestOrganisation): Future[TestOrganisationWithId] = {
    val organisationWithId = organisation.withPillar2Id(pillar2Id)
    repository.findByPillar2Id(pillar2Id).flatMap {
      case None =>
        Future.failed(OrganisationNotFound(pillar2Id))
      case Some(_) =>
        repository.update(organisationWithId).map(_ => organisationWithId)
    }
  }

  def deleteOrganisation(pillar2Id: String): Future[Unit] =
    repository.findByPillar2Id(pillar2Id).flatMap {
      case None =>
        Future.failed(OrganisationNotFound(pillar2Id))
      case Some(_) =>
        repository.delete(pillar2Id).map(_ => ())
    }

  def makeOrganisatonActive(pillar2Id: String): Future[Unit] =
    repository.findByPillar2Id(pillar2Id).flatMap {
      case None =>
        Future.failed(OrganisationNotFound(pillar2Id))
      case Some(orgWithId) =>
        val isInactive = orgWithId.organisation.accountStatus.inactive
        if (isInactive) {
          repository.update(orgWithId.copy(organisation = orgWithId.organisation.copy(accountStatus = AccountStatus(inactive = false)))).map(_ => ())
        } else {
          Future.successful(())
        }
    }

  def makeOrganisatonInactive(pillar2Id: String): Future[Unit] =
    repository.findByPillar2Id(pillar2Id).flatMap {
      case None =>
        Future.failed(OrganisationNotFound(pillar2Id))
      case Some(orgWithId) =>
        val isInactive = orgWithId.organisation.accountStatus.inactive
        if (isInactive) {
          Future.successful(())
        } else {
          repository.update(orgWithId.copy(organisation = orgWithId.organisation.copy(accountStatus = AccountStatus(inactive = true)))).map(_ => ())
        }
    }
}
