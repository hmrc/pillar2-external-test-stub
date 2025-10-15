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
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

case class UKTRLiabilityReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          Liability
) extends UKTRSubmission

object UKTRLiabilityReturn {

  implicit val uktrSubmissionDataFormat: OFormat[UKTRLiabilityReturn] = Json.format[UKTRLiabilityReturn]

  private[uktr] val totalLiabilityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalLiability = data.liabilities.totalLiabilityDTT + data.liabilities.totalLiabilityIIR + data.liabilities.totalLiabilityUTPR

    if (data.liabilities.totalLiability == totalLiability)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(InvalidTotalLiability)
      )
  }

  private[uktr] val totalLiabilityDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalDTTAmountOwed = data.liabilities.liableEntities.foldLeft(BigDecimal(0)) { (acc, entity) =>
      acc + entity.amountOwedDTT
    }

    val declaredTotal = data.liabilities.totalLiabilityDTT

    if (declaredTotal > 0 && declaredTotal == totalDTTAmountOwed)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityDTT
        )
      )
  }

  private[uktr] def totalLiabilityIIRRule(org: TestOrganisationWithId): ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalIIRAmountOwed = data.liabilities.liableEntities.foldLeft(BigDecimal(0)) { (acc, entity) =>
      acc + entity.amountOwedIIR
    }
    val totalIIR                     = data.liabilities.totalLiabilityIIR
    val notMTTLiableYetPositiveTotal = org.organisation.orgDetails.domesticOnly && !data.obligationMTT && totalIIR != 0

    if (!notMTTLiableYetPositiveTotal && totalIIR == totalIIRAmountOwed)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(InvalidTotalLiabilityIIR)
      )
  }

  private[uktr] def totalLiabilityUTPRRule(org: TestOrganisationWithId): ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalUTPRAmountOwed = data.liabilities.liableEntities.foldLeft(BigDecimal(0)) { (acc, entity) =>
      acc + entity.amountOwedUTPR
    }
    val totalUTPR                    = data.liabilities.totalLiabilityUTPR
    val notMTTLiableYetPositiveTotal = org.organisation.orgDetails.domesticOnly && !data.obligationMTT && totalUTPR != 0

    if (!notMTTLiableYetPositiveTotal && totalUTPR == totalUTPRAmountOwed)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityUTPR
        )
      )
  }

  private[uktr] val liabilityEntityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.nonEmpty) valid[UKTRLiabilityReturn](data)
    else throw HIPBadRequest()
  }

  private[uktr] val electionDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val isDTTSingleMember = data.liabilities.electionDTTSingleMember
    val subGroupDTTCount  = data.liabilities.numberSubGroupDTT

    val failedSingleMemberCheck = isDTTSingleMember && subGroupDTTCount < 1

    if (failedSingleMemberCheck) {
      invalid(UKTRSubmissionError(InvalidDTTElection))
    } else {
      valid[UKTRLiabilityReturn](data)
    }
  }

  private[uktr] val electionUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val isUTPRSingleMember = data.liabilities.electionUTPRSingleMember
    val subGroupUTPRCount  = data.liabilities.numberSubGroupUTPR

    val failedSingleMemberCheck = isUTPRSingleMember && subGroupUTPRCount < 1

    if (failedSingleMemberCheck) {
      invalid(UKTRSubmissionError(InvalidUTPRElection))
    } else {
      valid[UKTRLiabilityReturn](data)
    }
  }

  private[uktr] val ukChargeableEntityNameRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.ukChargeableEntityName.matches("^[a-zA-Z0-9 &'-]{1,160}$"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  // Validation rules for idType and idValue are now in the API, the two rules below are for added security
  private[uktr] val idTypeRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idType.nonEmpty && f.idType.length <= 4)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  private[uktr] val idValueRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idValue.matches("^[a-zA-Z0-9]{1,15}$"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  private[uktr] def mttLiabilityValidationRule(org: TestOrganisationWithId): ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val isDomestic = org.organisation.orgDetails.domesticOnly
    val hasMTTLiabilities =
      data.liabilities.totalLiabilityIIR > 0 ||
        data.liabilities.totalLiabilityUTPR > 0 ||
        data.liabilities.liableEntities.exists(entity => entity.amountOwedIIR > 0 || entity.amountOwedUTPR > 0)

    (isDomestic, data.obligationMTT, hasMTTLiabilities) match {
      case (true, _, true) =>
        invalid(UKTRSubmissionError(InvalidReturn))

      case (false, false, true) =>
        invalid(UKTRSubmissionError(InvalidReturn))

      case _ => valid(data)
    }
  }

  def uktrSubmissionValidator(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRLiabilityReturn]] =
    organisationService
      .getOrganisation(plrReference)
      .map { org =>
        ValidationRule.compose(
          mttLiabilityValidationRule(org),
          accountingPeriodMatchesOrgRule[UKTRLiabilityReturn](org, UKTRSubmissionError(InvalidReturn)),
          accountingPeriodSanityCheckRule[UKTRLiabilityReturn](UKTRSubmissionError(InvalidReturn)),
          electionDTTRule,
          electionUTPRRule,
          liabilityEntityRule,
          totalLiabilityRule,
          totalLiabilityDTTRule,
          totalLiabilityIIRRule(org),
          totalLiabilityUTPRRule(org),
          ukChargeableEntityNameRule,
          idTypeRule,
          idValueRule
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[UKTRLiabilityReturn](_ => invalid(UKTRSubmissionError(RequestCouldNotBeProcessed)))
      }
}
