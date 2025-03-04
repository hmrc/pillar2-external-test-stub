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
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}

case class UKTRNilReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityNilReturn
) extends UKTRSubmission {
  def isNilReturn: Boolean = true
}

object UKTRNilReturn {
  import UKTRError.UKTRErrorCodes._

  implicit val UKTRSubmissionNilReturnFormat: OFormat[UKTRNilReturn] = Json.format[UKTRNilReturn]

  def uktrNilReturnValidator(plrReference: String)(implicit os: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRNilReturn]] =
    os.getOrganisation(plrReference).map { org =>
      val isDomestic = org.organisation.orgDetails.domesticOnly
      ValidationRule[UKTRNilReturn] { nilReturn =>
        if (isDomestic && nilReturn.obligationMTT) {
          invalid(
            UKTRSubmissionError(
              INVALID_RETURN_093,
              "obligationMTT",
              "obligationMTT cannot be true for a domestic-only group"
            )
          )
        } else if (!isDomestic && nilReturn.electionUKGAAP) {
          invalid(
            UKTRSubmissionError(
              INVALID_RETURN_093,
              "electionUKGAAP",
              "electionUKGAAP can be true only for a domestic-only group"
            )
          )
        } else {
          valid(nilReturn)
        }
      }
    }
}
