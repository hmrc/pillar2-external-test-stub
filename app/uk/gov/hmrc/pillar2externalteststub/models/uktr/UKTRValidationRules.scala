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

import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import scala.concurrent.{ExecutionContext, Future}

object UKTRValidationRules {

  val boundaryUKTRAmount: BigDecimal = BigDecimal("9999999999999.99")

  def isValidUKTRAmount(number: BigDecimal): Boolean =
    number >= 0 &&
      number <= boundaryUKTRAmount &&
      number.scale <= 2

  // Common validation for obligationMTT - checks if domestic organizations can have obligationMTT set to true
  def obligationMTTRule[T <: UKTRSubmission](
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[T]] =
    organisationService.getOrganisation(plrReference).map { org =>
      val isDomestic = org.organisation.orgDetails.domesticOnly
      ValidationRule[T] { data =>
        if (data.obligationMTT && isDomestic) {
          invalid(
            UKTRSubmissionError(
              InvalidReturn
            )
          )
        } else valid[T](data)
      }
    }

  // Common validation for electionUKGAAP - checks if non-domestic organizations can have electionUKGAAP set to true
  def electionUKGAAPRule[T <: UKTRSubmission](
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[T]] =
    organisationService.getOrganisation(plrReference).map { org =>
      val isDomestic = org.organisation.orgDetails.domesticOnly
      ValidationRule[T] { data =>
        (data.electionUKGAAP, isDomestic) match {
          case (true, false) =>
            invalid(
              UKTRSubmissionError(
                InvalidReturn
              )
            )
          case _ => valid[T](data)
        }
      }
    }

  // Common validation for accounting period
  def accountingPeriodRule[T <: UKTRSubmission](
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[T]] =
    organisationService.getOrganisation(plrReference).map { org =>
      ValidationRule[T] { data =>
        if (
          data.accountingPeriodFrom == org.organisation.accountingPeriod.startDate &&
          data.accountingPeriodTo == org.organisation.accountingPeriod.endDate
        ) {
          valid[T](data)
        } else {
          invalid(
            UKTRSubmissionError(
              InvalidReturn
            )
          )
        }
      }
    }
}
