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
import scala.concurrent.{ExecutionContext, Future}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.{FailFast, ValidationRule}

import java.time.LocalDate

case class UKTRNilReturn(
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  obligationMTT:        Boolean,
  electionUKGAAP:       Boolean,
  liabilities:          LiabilityNilReturn
) extends UKTRSubmission

object UKTRNilReturn {
  implicit val UKTRSubmissionNilReturnFormat: OFormat[UKTRNilReturn] = Json.format[UKTRNilReturn]

  def uktrNilReturnValidator(
    plrReference:                 String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[UKTRNilReturn]] =
    for {
      obligationMTTRule    <- UKTRValidationRules.obligationMTTRule[UKTRNilReturn](plrReference)
      electionUKGAAPRule   <- UKTRValidationRules.electionUKGAAPRule[UKTRNilReturn](plrReference)
      accountingPeriodRule <- UKTRValidationRules.accountingPeriodRule[UKTRNilReturn](plrReference)
    } yield ValidationRule.compose(
      obligationMTTRule,
      electionUKGAAPRule,
      accountingPeriodRule
    )(FailFast)
}
