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

package uk.gov.hmrc.pillar2externalteststub.models.btn

import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNValidationError
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.FailFast
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.invalid
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
object BTNValidator {

  def btnValidator(
    pillar2Id: String
  )(implicit
    organisationService: OrganisationService,
    ec:                  ExecutionContext
  ): Future[ValidationRule[BTNRequest]] =
    organisationService
      .getOrganisation(pillar2Id)
      .map { org =>
        ValidationRule.compose(
          BaseSubmissionValidationRules.accountingPeriodMatchesOrgRule[BTNRequest](org, BTNValidationError(RequestCouldNotBeProcessed))
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[BTNRequest](_ => invalid(BTNValidationError(RequestCouldNotBeProcessed)))
      }
}
