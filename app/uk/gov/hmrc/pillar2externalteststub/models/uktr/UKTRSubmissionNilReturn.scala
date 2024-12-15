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

  val returnTypeRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { UKTRSubmissionNilReturn: UKTRSubmissionNilReturn =>
    if (UKTRSubmissionNilReturn.liabilities.returnType.equals(ReturnType.NIL_RETURN))
      valid[UKTRSubmissionNilReturn](UKTRSubmissionNilReturn)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "returnType",
          s"returnType must be ${ReturnType.NIL_RETURN}."
        )
      )
  }

  val accountingPeriodFromRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { UKTRSubmissionNilReturn: UKTRSubmissionNilReturn =>
    if (UKTRSubmission.isLocalDate(UKTRSubmissionNilReturn.accountingPeriodFrom))
      valid[UKTRSubmissionNilReturn](UKTRSubmissionNilReturn)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "accountingPeriodFrom",
          s"accountingPeriodFrom must be a valid date."
        )
      )
  }

  val accountingPeriodToRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { UKTRSubmissionNilReturn: UKTRSubmissionNilReturn =>
    if (UKTRSubmission.isLocalDate(UKTRSubmissionNilReturn.accountingPeriodTo))
      valid[UKTRSubmissionNilReturn](UKTRSubmissionNilReturn)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "accountingPeriodTo",
          s"accountingPeriodTo must be a valid date."
        )
      )
  }
  val obligationMTTRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { UKTRSubmissionNilReturn: UKTRSubmissionNilReturn =>
    if (UKTRSubmissionNilReturn.obligationMTT.isInstanceOf[Boolean])
      valid[UKTRSubmissionNilReturn](UKTRSubmissionNilReturn)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "obligationMTT",
          s"obligationMTT must be either true or false."
        )
      )
  }
  val electionUKGAAPRule: ValidationRule[UKTRSubmissionNilReturn] = ValidationRule { UKTRSubmissionNilReturn: UKTRSubmissionNilReturn =>
    if (UKTRSubmissionNilReturn.electionUKGAAP.isInstanceOf[Boolean])
      valid[UKTRSubmissionNilReturn](UKTRSubmissionNilReturn)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.BAD_REQUEST_400,
          "electionUKGAAP",
          s"electionUKGAAP must be either true or false."
        )
      )
  }

  implicit val UKTRSubmissionNilReturnValidator: ValidationRule[UKTRSubmissionNilReturn] =
    ValidationRule.compose(
      accountingPeriodFromRule,
      accountingPeriodToRule,
      obligationMTTRule,
      electionUKGAAPRule,
      returnTypeRule
    )(FailFast)
}
