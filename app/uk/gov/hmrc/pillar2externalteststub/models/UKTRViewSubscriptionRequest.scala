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

package uk.gov.hmrc.pillar2externalteststub.models

import play.api.libs.json.{Json, OFormat}

object UKTRViewSubscriptionRequest {
  implicit val accountStatusFormat: OFormat[AccountStatus] = Json.format[AccountStatus]
  implicit val accountingPeriodFormat: OFormat[AccountingPeriod] = Json.format[AccountingPeriod]
  implicit val filingMemberDetailsFormat: OFormat[FilingMemberDetails] = Json.format[FilingMemberDetails]
  implicit val contactDetailsFormat: OFormat[ContactDetails] = Json.format[ContactDetails]
  implicit val addressDetailsFormat: OFormat[AddressDetails] = Json.format[AddressDetails]
  implicit val upeDetailsFormat: OFormat[UpeDetails] = Json.format[UpeDetails]
  implicit val successDetailsFormat: OFormat[SuccessDetails] = Json.format[SuccessDetails]
  implicit val successResponseFormat: OFormat[SuccessResponse] = Json.format[SuccessResponse]

  implicit val failureFormat: OFormat[Failure] = Json.format[Failure]
  implicit val errorResponseFormat: OFormat[ErrorResponse] = Json.format[ErrorResponse]
}
