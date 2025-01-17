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

case class UKTRLiabilityReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          Liability
) extends UKTRSubmission

object UKTRLiabilityReturn {
  implicit val uktrSubmissionDataFormat: OFormat[UKTRLiabilityReturn] = Json.format[UKTRLiabilityReturn]

  private val boundaryUKTRAmount                    = BigDecimal("9999999999999.99")
  private val amountErrorMessage                    = s"must be a number between 0 and $boundaryUKTRAmount with up to 2 decimal places"
  private def isValidUKTRAmount(number: BigDecimal) = number >= 0 && number <= boundaryUKTRAmount && number.scale <= 2

  private def obligationMTTRule(plrReference: String): ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.obligationMTT && isDomesticOnly(plrReference)) {
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_RETURN_093,
          "obligationMTT",
          "obligationMTT cannot be true for a domestic-only group"
        )
      )
    } else valid[UKTRLiabilityReturn](data)
  }

  private def electionUKGAAPRule(plrReference: String): ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    (data.electionUKGAAP, isDomesticOnly(plrReference)) match {
      case (true, false) =>
        invalid(
          UKTRSubmissionError(
            UKTRErrorCodes.INVALID_RETURN_093,
            "electionUKGAAP",
            "electionUKGAAP can be true only for a domestic-only group"
          )
        )
      case _ => valid[UKTRLiabilityReturn](data)
    }
  }

  private val totalLiabilityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiability)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_TOTAL_LIABILITY_096,
          "totalLiability",
          s"totalLiability $amountErrorMessage"
        )
      )
  }

  private val totalLiabilityDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiabilityDTT)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_TOTAL_LIABILITY_DTT_098,
          "totalLiabilityDTT",
          s"totalLiabilityDTT $amountErrorMessage"
        )
      )
  }

  private val totalLiabilityIIRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiabilityIIR)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_TOTAL_LIABILITY_IIR_097,
          "totalLiabilityIIR",
          s"totalLiabilityIIR $amountErrorMessage"
        )
      )
  }

  private val totalLiabilityUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiabilityUTPR)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_TOTAL_LIABILITY_UTPR_099,
          "totalLiabilityUTPR",
          s"totalLiabilityUTPR $amountErrorMessage"
        )
      )
  }

  private val liabilityEntityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.nonEmpty) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.INVALID_RETURN_093,
          "liabilityEntity",
          "liabilityEntity cannot be empty"
        )
      )
  }

  private val ukChargeableEntityNameRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.ukChargeableEntityName.matches("^[a-zA-Z0-9 &'-]{1,160}$"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "ukChargeableEntityName",
          "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
        )
      )
  }

  private val idTypeRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idType.equals("UTR") || f.idType.equals("CRN"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "idType",
          "idType must be either UTR or CRN."
        )
      )
  }

  private val idValueRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idValue.matches("^[a-zA-Z0-9]{1,15}$"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "idValue",
          "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
        )
      )
  }

  private val amountOwedDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => isValidUKTRAmount(f.amountOwedDTT))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "amountOwedDTT",
          "amountOwedDTT " + amountErrorMessage
        )
      )
  }

  private val amountOwedIIRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => isValidUKTRAmount(f.amountOwedIIR))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "amountOwedIIR",
          "amountOwedIIR " + amountErrorMessage
        )
      )
  }

  private val amountOwedUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => isValidUKTRAmount(f.amountOwedUTPR))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "amountOwedUTPR",
          "amountOwedUTPR " + amountErrorMessage
        )
      )
  }

  implicit def uktrSubmissionValidator(plrReference: String): ValidationRule[UKTRLiabilityReturn] =
    ValidationRule.compose(
      obligationMTTRule(plrReference),
      electionUKGAAPRule(plrReference),
      totalLiabilityRule,
      totalLiabilityDTTRule,
      totalLiabilityIIRRule,
      totalLiabilityUTPRRule,
      liabilityEntityRule,
      ukChargeableEntityNameRule,
      idTypeRule,
      idValueRule,
      amountOwedDTTRule,
      amountOwedIIRRule,
      amountOwedUTPRRule
    )(FailFast)
}
