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

import cats.data.Validated.{Invalid, Valid}
import play.api.Logging
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNValidationError
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNValidator
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.ETMPInternalServerError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.TaxObligationAlreadyFulfilled
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType.BTN
import uk.gov.hmrc.pillar2externalteststub.repositories.{BTNSubmissionRepository, ObligationsAndSubmissionsRepository}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BTNService @Inject() (
  btnRepository:       BTNSubmissionRepository,
  oasRepository:       ObligationsAndSubmissionsRepository,
  organisationService: OrganisationService
)(using ec:            ExecutionContext)
    extends Logging {

  def submitBTN(pillar2Id: String, request: BTNRequest): Future[Boolean] = {
    logger.info(s"Submitting BTN for pillar2Id: $pillar2Id")

    for {
      validator    <- BTNValidator.btnValidator(pillar2Id)(using organisationService, ec)
      _            <- validateRequest(validator, request)
      _            <- checkForExistingSubmission(pillar2Id, request)
      submissionId <- btnRepository.insert(pillar2Id, request)
      _            <- oasRepository.insert(request, pillar2Id, submissionId)
      _            <- organisationService.makeOrganisatonInactive(pillar2Id)
    } yield true
  }

  def validateRequest(validator: ValidationRule[BTNRequest], request: BTNRequest): Future[Unit] =
    validator.validate(request) match {
      case Valid(_) => Future.successful(())
      case Invalid(errors) =>
        errors.head match {
          case BTNValidationError(error) => Future.failed(error)
          case _                         => Future.failed(ETMPInternalServerError)
        }
    }

  def checkForExistingSubmission(pillar2Id: String, request: BTNRequest): Future[Unit] =
    oasRepository.findByPillar2Id(pillar2Id, request.accountingPeriodFrom, request.accountingPeriodTo).map { obligationsAndSubmissions =>
      val latestSubmissionInRequestPeriod = obligationsAndSubmissions
        .filter(submission =>
          submission.accountingPeriod.startDate == request.accountingPeriodFrom &&
            submission.accountingPeriod.endDate == request.accountingPeriodTo
        )
        .sortBy(_.submittedAt)
        .lastOption

      latestSubmissionInRequestPeriod match {
        case Some(submission) if submission.submissionType == BTN =>
          throw TaxObligationAlreadyFulfilled
        case _ =>
          ()
      }
    }
}
