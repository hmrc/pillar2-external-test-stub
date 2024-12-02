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
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{ValidationError, ValidationRule}

import java.time.LocalDate

case class UkChargeableEntityNameError(errorCode: String, errorMessage: String, field: String) extends ValidationError

case class UktrSubmissionData(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityData
) extends UktrSubmission

object UktrSubmissionData {
  implicit val uktrSubmissionDataFormat: OFormat[UktrSubmissionData] = Json.format[UktrSubmissionData]

  val ukChargeableEntityNameRule: ValidationRule[UktrSubmissionData] = ValidationRule { uktrSubmissionData: UktrSubmissionData =>
    if (uktrSubmissionData.liabilities.liableEntities.forall(f => f.ukChargeableEntityName.matches("^[a-zA-Z0-9 &'-]{1,160}$")))
      valid[UktrSubmissionData](uktrSubmissionData)
    else
      invalid(
        UkChargeableEntityNameError(
          "INVALID_UK_CHARGEABLE_ENTITY_NAME",
          "UK Chargeable Entity Name must be 1-160 characters and can only contain letters, numbers, spaces, &, ', and -",
          "ukChargeableEntityName"
        )
      )
  }

  implicit val uktrSubmissionValidator: ValidationRule[UktrSubmissionData] = ukChargeableEntityNameRule
}
