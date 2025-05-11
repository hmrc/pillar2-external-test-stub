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
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.ETMPInternalServerError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.TaxObligationAlreadyFulfilled
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRValidationError
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRValidator
import uk.gov.hmrc.pillar2externalteststub.repositories.{GIRSubmissionRepository, ObligationsAndSubmissionsRepository}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GIRService @Inject() (
  girRepository:       GIRSubmissionRepository,
  oasRepository:       ObligationsAndSubmissionsRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends Logging {

  def submitGIR(pillar2Id: String, request: GIRRequest): Future[Boolean] = {
    logger.info(s"Submitting GIR for pillar2Id: $pillar2Id")

    for {
      validator    <- GIRValidator.girValidator(pillar2Id)(organisationService, ec)
      _            <- validateRequest(validator, request)
      _            <- validateNoExistingSubmissionForPeriod(pillar2Id, request)
      submissionId <- girRepository.insert(pillar2Id, request)
      _            <- oasRepository.insert(request, pillar2Id, submissionId)
    } yield true
  }

  private def validateRequest(validator: ValidationRule[GIRRequest], request: GIRRequest): Future[Unit] =
    validator.validate(request) match {
      case Valid(_) => Future.successful(())
      case Invalid(errors) =>
        errors.head match {
          case GIRValidationError(error) => Future.failed(error)
          case _                        => Future.failed(ETMPInternalServerError)
        }
    }

    private def validateNoExistingSubmissionForPeriod(pillar2Id: String, request: GIRRequest): Future[Unit] =
    girRepository.findByPillar2Id(pillar2Id).flatMap { submissions =>
      submissions.find(submission =>
        submission.accountingPeriodFrom == request.accountingPeriodFrom &&
          submission.accountingPeriodTo == request.accountingPeriodTo
      ) match {
        case Some(_) => Future.failed(TaxObligationAlreadyFulfilled)
        case None    => Future.successful(())
      }
    }
}