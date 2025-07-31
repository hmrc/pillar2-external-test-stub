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

import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{AccountingPeriodUnderEnquiry, NoActiveSubscription, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import scala.concurrent.{ExecutionContext, Future}
object BTNValidator {

  private def underEnquiryCheck(org: TestOrganisationWithId): ValidationRule[BTNRequest] =
    ValidationRule[BTNRequest] { request =>
      org.organisation.accountingPeriod.underEnquiry match {
        case Some(true) => invalid(BTNValidationError(AccountingPeriodUnderEnquiry))
        case _          => valid(request)
      }
    }

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
          BaseSubmissionValidationRules.accountingPeriodMatchesOrgRule[BTNRequest](org, BTNValidationError(RequestCouldNotBeProcessed)),
          underEnquiryCheck(org)
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[BTNRequest](_ => invalid(BTNValidationError(NoActiveSubscription)))
      }
}
