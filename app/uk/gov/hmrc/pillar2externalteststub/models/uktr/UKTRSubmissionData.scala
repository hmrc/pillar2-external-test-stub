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

case class UKTRSubmissionData(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityData
) extends UKTRSubmission

object UKTRSubmissionData {
  implicit val uktrSubmissionDataFormat: OFormat[UKTRSubmissionData] = Json.format[UKTRSubmissionData]
  val amountErrorMessage =
    " must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."

  private def isValidUKTRAmount(number: String): Boolean = {
    val pattern = """^\d{1,13}\.{0,1}\d{0,2}$""".r
    number match {
      case pattern() if number.length <= 13 && number.toDouble > 0 => true
      case _                                                       => false
    }
  }

  private def electionUKGAAPRule(plrReference: String): ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData =>
    val subscriptionResponse = SubscriptionHelper.retrieveSubscription(plrReference)._2
    val isDomesticOnly       = if (subscriptionResponse == successfulDomesticOnlyResponse(plrReference)) true else false
    (uktrSubmissionData.electionUKGAAP, isDomesticOnly) match {
      case (_, true) | (false, false) => valid[UKTRSubmissionData](uktrSubmissionData)
      case _ =>
        invalid(
          UktrSubmissionError(
            UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
            "electionUKGAAP",
            "electionUKGAAP can be true only for a domestic-only group"
          )
        )
    }
  }

  private val ukChargeableEntityNameRule: ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData: UKTRSubmissionData =>
    if (
      uktrSubmissionData.liabilities.liableEntities
        .forall(f => f.ukChargeableEntityName.matches("^[a-zA-Z0-9 &'-]{1,160}$"))
    )
      valid[UKTRSubmissionData](uktrSubmissionData)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "ukChargeableEntityName",
          "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
        )
      )
  }

  private val idTypeRule: ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData: UKTRSubmissionData =>
    if (
      uktrSubmissionData.liabilities.liableEntities
        .forall(f => f.idType.equals("UTR") || f.idType.equals("CRN"))
    )
      valid[UKTRSubmissionData](uktrSubmissionData)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "idType",
          "idType must be either UTR or CRN."
        )
      )
  }

  private val idValueRule: ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData: UKTRSubmissionData =>
    if (
      uktrSubmissionData.liabilities.liableEntities
        .forall(f => f.idValue.matches("^[a-zA-Z0-9]{1,15}$"))
    )
      valid[UKTRSubmissionData](uktrSubmissionData)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "idValue",
          "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
        )
      )
  }

  private val amountOwedDTTRule: ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData: UKTRSubmissionData =>
    if (
      uktrSubmissionData.liabilities.liableEntities
        .forall(f => isValidUKTRAmount(f.amountOwedDTT.toString()))
    )
      valid[UKTRSubmissionData](uktrSubmissionData)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "amountOwedDTT",
          "amountOwedDTT" + amountErrorMessage
        )
      )
  }

  private val amountOwedIIRRule: ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData: UKTRSubmissionData =>
    if (
      uktrSubmissionData.liabilities.liableEntities
        .forall(f => isValidUKTRAmount(f.amountOwedIIR.toString()))
    )
      valid[UKTRSubmissionData](uktrSubmissionData)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "amountOwedIIR",
          "amountOwedIIR" + amountErrorMessage
        )
      )
  }

  val amountOwedUTPRRule: ValidationRule[UKTRSubmissionData] = ValidationRule { uktrSubmissionData: UKTRSubmissionData =>
    if (
      uktrSubmissionData.liabilities.liableEntities
        .forall(f => isValidUKTRAmount(f.amountOwedUTPR.toString()))
    )
      valid[UKTRSubmissionData](uktrSubmissionData)
    else
      invalid(
        UktrSubmissionError(
          UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "amountOwedUTPR",
          "amountOwedUTPR" + amountErrorMessage
        )
      )
  }

  implicit def uktrSubmissionValidator(plrReference: String): ValidationRule[UKTRSubmissionData] =
    ValidationRule.compose(
      electionUKGAAPRule(plrReference),
      ukChargeableEntityNameRule,
      idTypeRule,
      idValueRule,
      amountOwedDTTRule,
      amountOwedIIRRule,
      amountOwedUTPRRule
    )(FailFast)
}
