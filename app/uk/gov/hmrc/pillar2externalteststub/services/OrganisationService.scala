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

import uk.gov.hmrc.pillar2externalteststub.models.organisation.{OrganisationDetails, OrganisationDetailsWithId}
import uk.gov.hmrc.pillar2externalteststub.repositories.OrganisationRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationService @Inject()(
  repository: OrganisationRepository
)(implicit ec: ExecutionContext) {

  def createOrganisation(pillar2Id: String, details: OrganisationDetails): Future[Either[String, OrganisationDetailsWithId]] = {
    val organisationWithId = details.withPillar2Id(pillar2Id)
    repository.insert(organisationWithId).map {
      case true => Right(organisationWithId)
      case false => Left("Failed to create organisation")
    }
  }

  def getOrganisation(pillar2Id: String): Future[Option[OrganisationDetailsWithId]] =
    repository.findByPillar2Id(pillar2Id)

  def updateOrganisation(pillar2Id: String, details: OrganisationDetails): Future[Either[String, OrganisationDetailsWithId]] = {
    val organisationWithId = details.withPillar2Id(pillar2Id)
    repository.update(organisationWithId).map {
      case true => Right(organisationWithId)
      case false => Left("Failed to update organisation")
    }
  }

  def deleteOrganisation(pillar2Id: String): Future[Boolean] =
    repository.delete(pillar2Id)
} 