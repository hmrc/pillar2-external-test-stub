/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.Logging
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.btn.mongo.BTNSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.repositories.{BTNSubmissionRepository, ObligationsAndSubmissionsRepository}

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BTNService @Inject() (
  btnRepository:       BTNSubmissionRepository,
  oasRepository:       ObligationsAndSubmissionsRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends Logging {

  def submitBTN(pillar2Id: String, request: BTNRequest): Future[Boolean] = {
    logger.info(s"Submitting BTN for pillar2Id: $pillar2Id")
    organisationService
      .getOrganisation(pillar2Id)
      .flatMap { testOrg =>
        if (
          testOrg.organisation.accountingPeriod.startDate == request.accountingPeriodFrom &&
          testOrg.organisation.accountingPeriod.endDate == request.accountingPeriodTo
        ) {
          btnRepository.findByPillar2Id(pillar2Id).flatMap { submissions =>
            if (
              submissions.exists(submission =>
                submission.accountingPeriodFrom == request.accountingPeriodFrom &&
                  submission.accountingPeriodTo == request.accountingPeriodTo
              )
            )
              Future.failed(DuplicateSubmission)
            else
              btnRepository.insert(pillar2Id, request).flatMap { submissionId =>
                oasRepository.insert(request, pillar2Id, submissionId)
              }
          }
        } else Future.failed(RequestCouldNotBeProcessed)
      }
      .recoverWith { case _: OrganisationNotFound =>
        Future.failed(NoActiveSubscription)
      }
  }

  def findBTNByPillar2Id(pillar2Id: String): Future[Seq[BTNSubmission]] =
    btnRepository.findByPillar2Id(pillar2Id)
}
