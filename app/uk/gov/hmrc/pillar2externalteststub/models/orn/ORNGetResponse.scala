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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.models.orn.mongo.ORNSubmission

import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}

case class ORNGetResponse(success: ORNGetSuccess)

object ORNGetResponse {
  given format: OFormat[ORNGetResponse] = Json.format[ORNGetResponse]

  def fromSubmission(submission: ORNSubmission): ORNGetResponse = ORNGetResponse(
    success = ORNGetSuccess(
      processingDate = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
      accountingPeriodFrom = submission.accountingPeriodFrom,
      accountingPeriodTo = submission.accountingPeriodTo,
      filedDateGIR = submission.filedDateGIR,
      countryGIR = submission.countryGIR,
      reportingEntityName = submission.reportingEntityName,
      TIN = submission.TIN,
      issuingCountryTIN = submission.issuingCountryTIN
    )
  )
}

case class ORNGetSuccess(
  processingDate:       ZonedDateTime,
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  filedDateGIR:         LocalDate,
  countryGIR:           String,
  reportingEntityName:  String,
  TIN:                  String,
  issuingCountryTIN:    String
)

object ORNGetSuccess {
  given format: OFormat[ORNGetSuccess] = Json.format[ORNGetSuccess]
}
