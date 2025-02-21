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

package uk.gov.hmrc.pillar2externalteststub.models.subscription

import play.api.libs.json.{Json, OWrites}

case class UPEDetailsResponse(
  customerIdentification1: String,
  customerIdentification2: String,
  organisationName:        String,
  registrationDate:        String,
  domesticOnly:            Boolean,
  filingMember:            Boolean
)

object UPEDetailsResponse {
  implicit val writes: OWrites[UPEDetailsResponse] = Json.writes[UPEDetailsResponse]
}

case class AddressDetailsResponse(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  postCode:     Option[String],
  countryCode:  String
)

object AddressDetailsResponse {
  implicit val writes: OWrites[AddressDetailsResponse] = Json.writes[AddressDetailsResponse]
}

case class ContactDetailsResponse(
  name:         String,
  telephone:    Option[String],
  emailAddress: String
)

object ContactDetailsResponse {
  implicit val writes: OWrites[ContactDetailsResponse] = Json.writes[ContactDetailsResponse]
}

case class FilingMemberDetailsResponse(
  safeId:                  String,
  organisationName:        String,
  customerIdentification1: String,
  customerIdentification2: String
)

object FilingMemberDetailsResponse {
  implicit val writes: OWrites[FilingMemberDetailsResponse] = Json.writes[FilingMemberDetailsResponse]
}

case class AccountingPeriodResponse(
  startDate: String,
  endDate:   String,
  dueDate:   Option[String]
)

object AccountingPeriodResponse {
  implicit val writes: OWrites[AccountingPeriodResponse] = Json.writes[AccountingPeriodResponse]
}

case class AccountStatusResponse(
  inactive: Boolean
)

object AccountStatusResponse {
  implicit val writes: OWrites[AccountStatusResponse] = Json.writes[AccountStatusResponse]
}
