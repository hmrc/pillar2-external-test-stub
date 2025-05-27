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

package uk.gov.hmrc.pillar2externalteststub.models.orn

import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmissionValidationRules.countryList
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{ValidationError, ValidationRule}

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

case class ORNValidationError(error: ETMPError) extends ValidationError {
  override def errorCode:    String = error.code
  override def errorMessage: String = error.message
}

object ORNValidationRules {

  def domesticOnlyRule(testOrg: TestOrganisationWithId): ValidationRule[ORNRequest] = {
    val isDomesticOnly = testOrg.organisation.orgDetails.domesticOnly
    ValidationRule[ORNRequest] { request =>
      if (isDomesticOnly) invalid(ORNValidationError(RequestCouldNotBeProcessed))
      else valid(request)
    }
  }

  /**
   * BTN flag status validation rule
   * Checks if the BTN flag is active (accountStatus.inactive = true)
   * Returns error 003 (RequestCouldNotBeProcessed) if BTN flag is active
   */
  def btnStatusRule(pillar2Id: String)(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[ORNRequest]] =
    organisationService.isBtnFlagActive(pillar2Id).map { isBtnActive =>
      ValidationRule[ORNRequest] { request =>
        if (isBtnActive) invalid(ORNValidationError(RequestCouldNotBeProcessed))
        else valid(request)
      }
    }

  def filedDateGIRRule: ValidationRule[ORNRequest] =
    ValidationRule[ORNRequest] { request =>
      val filedGirDateFutureDate = request.filedDateGIR.isAfter(LocalDate.now)
      if (filedGirDateFutureDate) invalid(ORNValidationError(RequestCouldNotBeProcessed))
      else valid(request)
    }

  def countryISOComplianceRule: ValidationRule[ORNRequest] =
    ValidationRule[ORNRequest] { request =>
      if (!countryList.contains(request.countryGIR) || !countryList.contains(request.issuingCountryTIN)) {
        invalid(ORNValidationError(RequestCouldNotBeProcessed))
      } else valid(request)
    }
}
