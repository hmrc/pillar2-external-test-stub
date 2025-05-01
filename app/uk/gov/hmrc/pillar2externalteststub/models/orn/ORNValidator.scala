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

package uk.gov.hmrc.pillar2externalteststub.models.orn

import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{NoActiveSubscription, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.invalid
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import scala.concurrent.{ExecutionContext, Future}

object ORNValidator {

  def ornValidator(
    pillar2Id: String
  )(implicit
    organisationService: OrganisationService,
    ec:                  ExecutionContext
  ): Future[ValidationRule[ORNRequest]] =
    // Fetch the organisation once
    organisationService
      .getOrganisation(pillar2Id)
      .map { org =>
        val domesticRule = ORNValidationRules.domesticOnlyRule(org)
        val accountingPeriodRule = BaseSubmissionValidationRules.accountingPeriodSanityCheckRule[ORNRequest](
          ORNValidationError(RequestCouldNotBeProcessed)
        )
        val filedDateGIRRule = ORNValidationRules.filedDateGIRRule

        ValidationRule.compose(
          domesticRule,
          accountingPeriodRule,
          filedDateGIRRule
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[ORNRequest](_ => invalid(ORNValidationError(NoActiveSubscription)))
      }
}
