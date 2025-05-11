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

package uk.gov.hmrc.pillar2externalteststub.models.gir

import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{NoActiveSubscription, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.invalid
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import scala.concurrent.{ExecutionContext, Future}

case class GIRValidationError(error: uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError) extends uk.gov.hmrc.pillar2externalteststub.validation.ValidationError {
  override def errorCode: String = error.code
  override def errorMessage: String = error.message
}

object GIRValidator {
  def girValidator(
    pillar2Id: String
  )(implicit
    organisationService: OrganisationService,
    ec: ExecutionContext
  ): Future[ValidationRule[GIRRequest]] =
    organisationService
      .getOrganisation(pillar2Id)
      .map { org =>
        ValidationRule.compose(
          BaseSubmissionValidationRules.accountingPeriodMatchesOrgRule[GIRRequest](org, GIRValidationError(RequestCouldNotBeProcessed))
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[GIRRequest](_ => invalid(GIRValidationError(NoActiveSubscription)))
      }
} 