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

import uk.gov.hmrc.pillar2externalteststub.helpers.SubscriptionHelper
import uk.gov.hmrc.pillar2externalteststub.models.error.{OrganisationAlreadyExists, OrganisationNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{AccountStatus, TestOrganisation, TestOrganisationWithId}
import uk.gov.hmrc.pillar2externalteststub.models.subscription.{SubscriptionResponse, SubscriptionSuccessResponse}
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

  /**
   * Get organisation with enriched subscription data including BTN flag status
   * This method calls the subscription service (EPID 1457) to get the latest account status
   */
  def getOrganisationWithSubscription(pillar2Id: String): Future[TestOrganisationWithId] =
    for {
      org <- getOrganisation(pillar2Id)
      subscriptionData <- getSubscriptionData(pillar2Id)
      enrichedOrg = enrichOrganisationWithSubscription(org, subscriptionData)
    } yield enrichedOrg

  /**
   * Get BTN flag status for a given pillar2Id
   * Returns true if BTN flag is active (inactive = true), false otherwise
   */
  def getBtnFlagStatus(pillar2Id: String): Future[Boolean] =
    getSubscriptionData(pillar2Id).map {
      case success: SubscriptionSuccessResponse => success.accountStatus.inactive
      case _ => false // Default to false if subscription data is not available
    }

  /**
   * Check if BTN flag is active (inactive = true)
   * This is the method that should be used in validation rules
   */
  def isBtnFlagActive(pillar2Id: String): Future[Boolean] = getBtnFlagStatus(pillar2Id)

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

  // Private helper methods

  /**
   * Retrieve subscription data using the existing SubscriptionHelper (EPID 1457)
   */
  private def getSubscriptionData(pillar2Id: String): Future[SubscriptionResponse] =
    Future.successful {
      val (_, response) = SubscriptionHelper.retrieveSubscription(pillar2Id)
      response
    }

  /**
   * Enrich organisation data with subscription information
   */
  private def enrichOrganisationWithSubscription(
    org: TestOrganisationWithId, 
    subscriptionData: SubscriptionResponse
  ): TestOrganisationWithId = {
    subscriptionData match {
      case success: SubscriptionSuccessResponse =>
        val updatedAccountStatus = AccountStatus(inactive = success.accountStatus.inactive)
        val updatedOrganisation = org.organisation.copy(accountStatus = updatedAccountStatus)
        org.copy(organisation = updatedOrganisation)
      case _ =>
        // If subscription data is not available, keep the existing organisation data
        org
    }
  }
}
