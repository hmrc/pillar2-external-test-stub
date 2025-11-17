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

package uk.gov.hmrc.pillar2externalteststub.models.uktr

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules.{accountingPeriodMatchesOrgRule, accountingPeriodSanityCheckRule}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.*
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.invalid
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

case class UKTRNilReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityNilReturn
) extends UKTRSubmission

object UKTRNilReturn {
  given UKTRSubmissionNilReturnFormat: OFormat[UKTRNilReturn] = Json.format[UKTRNilReturn]

  def uktrNilReturnValidator(
    plrReference:              String
  )(using organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRNilReturn]] =
    organisationService
      .getOrganisation(plrReference)
      .map { org =>
        ValidationRule.compose(
          accountingPeriodMatchesOrgRule[UKTRNilReturn](org, UKTRSubmissionError(InvalidReturn)),
          accountingPeriodSanityCheckRule[UKTRNilReturn](UKTRSubmissionError(InvalidReturn))
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[UKTRNilReturn](_ => invalid(UKTRSubmissionError(RequestCouldNotBeProcessed)))
      }
}
