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
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

object UKTRValidationRules {

  // Common validation for obligationMTT - checks if domestic organisations can have obligationMTT set to true
  def obligationMTTRule[T <: UKTRSubmission](
    org: TestOrganisationWithId
  ): ValidationRule[T] =
    ValidationRule[T] { data =>
      if (data.obligationMTT && org.organisation.orgDetails.domesticOnly) {
        invalid(
          UKTRSubmissionError(
            InvalidReturn
          )
        )
      } else valid[T](data)
    }
}
