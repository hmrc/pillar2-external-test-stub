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
import uk.gov.hmrc.pillar2externalteststub.helpers.SubscriptionHelper.isDomesticOnly
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import java.time.LocalDate

case class UKTRNilReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityNilReturn
) extends UKTRSubmission

object UKTRNilReturn {
  implicit val UKTRSubmissionNilReturnFormat: OFormat[UKTRNilReturn] = Json.format[UKTRNilReturn]

  private def obligationMTTRule(plrReference: String): ValidationRule[UKTRNilReturn] = ValidationRule { data =>
    if (data.obligationMTT && isDomesticOnly(plrReference)) {
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_RETURN_093,
          "obligationMTT",
          "obligationMTT cannot be true for a domestic-only group"
        )
      )
    } else valid[UKTRNilReturn](data)
  }

  private def electionUKGAAPRule(plrReference: String): ValidationRule[UKTRNilReturn] = ValidationRule { data =>
    (data.electionUKGAAP, isDomesticOnly(plrReference)) match {
      case (true, false) =>
        invalid(
          UKTRSubmissionError(
            UKTRErrorCodes.INVALID_RETURN_093,
            "electionUKGAAP",
            "electionUKGAAP can be true only for a domestic-only group"
          )
        )
      case _ => valid[UKTRNilReturn](data)
    }
  }

  private val accountingPeriodRule: ValidationRule[UKTRNilReturn] = ValidationRule { data =>
    if (data.accountingPeriodTo.isAfter(data.accountingPeriodFrom)) valid[UKTRNilReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "accountingPeriod",
          "Accounting period end date must be after start date"
        )
      )
  }

  implicit def uktrSubmissionValidator(plrReference: String): ValidationRule[UKTRNilReturn] =
    ValidationRule.compose(
      accountingPeriodRule,
      obligationMTTRule(plrReference),
      electionUKGAAPRule(plrReference)
    )(FailFast)
}
