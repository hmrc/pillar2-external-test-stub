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
import uk.gov.hmrc.pillar2externalteststub.helpers.SubscriptionHelper
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse.successfulDomesticOnlyResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.UktrErrorCodes
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import java.time.LocalDate

case class UKTRSubmissionNilReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityNilReturn
) extends UKTRSubmission

object UKTRSubmissionNilReturn {
  implicit val UKTRSubmissionNilReturnFormat: OFormat[UKTRSubmissionNilReturn] = Json.format[UKTRSubmissionNilReturn]

  private def isDomesticOnly(plrReference: String): Boolean =
    if (SubscriptionHelper.retrieveSubscription(plrReference)._2 == successfulDomesticOnlyResponse) true else false

  private val returnTypeRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { data =>
    if (data.liabilities.returnType.equals(ReturnType.NIL_RETURN))
      valid[UKTRSubmissionNilReturn](data)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "returnType",
          s"returnType must be ${ReturnType.NIL_RETURN}."
        )
      )
  }

  private val accountingPeriodFromRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { data =>
    if (UKTRSubmission.isLocalDate(data.accountingPeriodFrom))
      valid[UKTRSubmissionNilReturn](data)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "accountingPeriodFrom",
          s"accountingPeriodFrom must be a valid date."
        )
      )
  }

  private val accountingPeriodToRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { data =>
    if (UKTRSubmission.isLocalDate(data.accountingPeriodTo))
      valid[UKTRSubmissionNilReturn](data)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "accountingPeriodTo",
          s"accountingPeriodTo must be a valid date."
        )
      )
  }
  private def obligationMTTRule(plrReference: String): ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { data =>
    if (data.obligationMTT == isDomesticOnly(plrReference)) {
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "obligationMTT",
          "obligationMTT cannot be true for a domestic-only group or false for a non-domestic-only group"
        )
      )
    } else valid[UKTRSubmissionNilReturn](data)
  }

  private def electionUKGAAPRule(plrReference: String): ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { data =>
    (data.electionUKGAAP, isDomesticOnly(plrReference)) match {
      case (true, false) =>
        invalid(
          UktrSubmissionError(
            UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
            "electionUKGAAP",
            "electionUKGAAP can be true only for a domestic-only group"
          )
        )
      case _ => valid[UKTRSubmissionNilReturn](data)
    }
  }

  implicit def uktrNilReturnValidator(plrReference: String): ValidationRule[UKTRSubmissionNilReturn] =
    ValidationRule.compose(
      accountingPeriodFromRule,
      accountingPeriodToRule,
      obligationMTTRule(plrReference),
      electionUKGAAPRule(plrReference),
      returnTypeRule
    )(FailFast)
}
