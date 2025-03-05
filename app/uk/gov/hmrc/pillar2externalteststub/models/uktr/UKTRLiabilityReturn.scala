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
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._

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

  private val boundaryUKTRAmount = BigDecimal("9999999999999.99")
  private def isValidUKTRAmount(number: BigDecimal) =
    number >= 0 &&
      number <= boundaryUKTRAmount &&
      number.scale <= 2 &&
      number.toString.matches("^\\d+(\\.\\d{1,2})?$")

  private def obligationMTTRule(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRLiabilityReturn]] =
    organisationService.getOrganisation(plrReference).map { org =>
      val isDomestic = org.organisation.orgDetails.domesticOnly
      ValidationRule[UKTRLiabilityReturn] { data =>
        if (data.obligationMTT && isDomestic) {
          invalid(
            UKTRSubmissionError(
              InvalidReturn
            )
          )
        } else valid[UKTRLiabilityReturn](data)
      }
    }

  private def electionUKGAAPRule(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRLiabilityReturn]] =
    organisationService.getOrganisation(plrReference).map { org =>
      val isDomestic = org.organisation.orgDetails.domesticOnly
      ValidationRule[UKTRLiabilityReturn] { data =>
        (data.electionUKGAAP, isDomestic) match {
          case (true, false) =>
            invalid(
              UKTRSubmissionError(InvalidReturn)
            )
          case _ => valid[UKTRLiabilityReturn](data)
        }
      }
    }

  private def accountingPeriodRule(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRLiabilityReturn]] =
    organisationService.getOrganisation(plrReference).map { org =>
      ValidationRule[UKTRLiabilityReturn] { data =>
        if (
          data.accountingPeriodFrom.isBefore(org.organisation.accountingPeriod.startDate) || data.accountingPeriodTo
            .isAfter(org.organisation.accountingPeriod.endDate)
        ) {
          invalid(
            UKTRSubmissionError(
              InvalidReturn
            )
          )
        } else valid[UKTRLiabilityReturn](data)
      }
    }

  private val totalLiabilityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiability)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(InvalidTotalLiability)
      )
  }

  private val totalLiabilityDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiabilityDTT)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityDTT
        )
      )
  }

  private val totalLiabilityIIRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiabilityIIR)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(InvalidTotalLiabilityIIR)
      )
  }

  private val totalLiabilityUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (isValidUKTRAmount(data.liabilities.totalLiabilityUTPR)) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityUTPR
        )
      )
  }

  private val liabilityEntityRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.nonEmpty) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  private val ukChargeableEntityNameRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.ukChargeableEntityName.matches("^[a-zA-Z0-9 &'-]{1,160}$"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  private val idTypeRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idType.equals("UTR") || f.idType.equals("CRN"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  private val idValueRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => f.idValue.matches("^[a-zA-Z0-9]{1,15}$"))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidReturn
        )
      )
  }

  private val amountOwedDTTRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => isValidUKTRAmount(f.amountOwedDTT))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityDTT
        )
      )
  }

  private val amountOwedIIRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => isValidUKTRAmount(f.amountOwedIIR))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityIIR
        )
      )
  }

  private val amountOwedUTPRRule: ValidationRule[UKTRLiabilityReturn] = ValidationRule { data =>
    if (data.liabilities.liableEntities.forall(f => isValidUKTRAmount(f.amountOwedUTPR))) valid[UKTRLiabilityReturn](data)
    else
      invalid(
        UKTRSubmissionError(
          InvalidTotalLiabilityUTPR
        )
      )
  }

  def uktrSubmissionValidator(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRLiabilityReturn]] =
    for {
      obligationMTTRule    <- obligationMTTRule(plrReference)
      electionUKGAAPRule   <- electionUKGAAPRule(plrReference)
      accountingPeriodRule <- accountingPeriodRule(plrReference)
    } yield ValidationRule.compose(
      obligationMTTRule,
      electionUKGAAPRule,
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
