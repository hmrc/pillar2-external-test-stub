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
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
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
) extends UKTRSubmission {
  def isNilReturn: Boolean = false
}

object UKTRLiabilityReturn {

  implicit val uktrSubmissionDataFormat: OFormat[UKTRLiabilityReturn] = Json.format[UKTRLiabilityReturn]

  private[uktr] val totalLiabilityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalLiability = data.liabilities.totalLiabilityDTT + data.liabilities.totalLiabilityIIR + data.liabilities.totalLiabilityUTPR

    if (UKTRValidationRules.isValidUKTRAmount(data.liabilities.totalLiability) && data.liabilities.totalLiability == totalLiability)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  private[uktr] val totalLiabilityDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalDTTAmountOwed = data.liabilities.liableEntities.foldLeft(BigDecimal(0)) { (acc, entity) =>
      acc + entity.amountOwedDTT
    }

    if (UKTRValidationRules.isValidUKTRAmount(data.liabilities.totalLiabilityDTT) && data.liabilities.totalLiabilityDTT == totalDTTAmountOwed)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  private[uktr] val totalLiabilityIIRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalIIRAmountOwed = data.liabilities.liableEntities.foldLeft(BigDecimal(0)) { (acc, entity) =>
      acc + entity.amountOwedIIR
    }

    if (UKTRValidationRules.isValidUKTRAmount(data.liabilities.totalLiabilityIIR) && data.liabilities.totalLiabilityIIR == totalIIRAmountOwed)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  private[uktr] val totalLiabilityUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    val totalUTPRAmountOwed = data.liabilities.liableEntities.foldLeft(BigDecimal(0)) { (acc, entity) =>
      acc + entity.amountOwedUTPR
    }

    if (UKTRValidationRules.isValidUKTRAmount(data.liabilities.totalLiabilityUTPR) && data.liabilities.totalLiabilityUTPR == totalUTPRAmountOwed)
      valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  private[uktr] val liabilityEntityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.nonEmpty) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
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

  private[uktr] val idTypeRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idType.equals("UTR") || f.idType.equals("CRN"))) valid[UKTRLiabilityReturn](data)
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

  private[uktr] val amountOwedDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => UKTRValidationRules.isValidUKTRAmount(f.amountOwedDTT))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  private[uktr] val amountOwedIIRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => UKTRValidationRules.isValidUKTRAmount(f.amountOwedIIR))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  private[uktr] val amountOwedUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => UKTRValidationRules.isValidUKTRAmount(f.amountOwedUTPR))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(ETMPBadRequest)
      )
  }

  def uktrSubmissionValidator(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRLiabilityReturn]] =
    organisationService
      .getOrganisation(plrReference)
      .map { org =>
        ValidationRule.compose(
          UKTRValidationRules.obligationMTTRule[UKTRLiabilityReturn](org),
          UKTRValidationRules.electionUKGAAPRule[UKTRLiabilityReturn](org),
          BaseSubmissionValidationRules.accountingPeriodMatchesOrgRule[UKTRLiabilityReturn](org, UKTRSubmissionError(InvalidReturn)),
          liabilityEntityRule,
          totalLiabilityRule,
          totalLiabilityDTTRule,
          totalLiabilityIIRRule,
          totalLiabilityUTPRRule,
          ukChargeableEntityNameRule,
          idTypeRule,
          idValueRule,
          amountOwedDTTRule,
          amountOwedIIRRule,
          amountOwedUTPRRule
        )(FailFast)
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[UKTRLiabilityReturn](_ => invalid(UKTRSubmissionError(RequestCouldNotBeProcessed)))
      }
}
