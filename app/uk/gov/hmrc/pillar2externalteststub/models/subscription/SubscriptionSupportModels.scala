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

case class UPEDetails(
  customerIdentification1: String,
  customerIdentification2: String,
  organisationName:        String,
  registrationDate:        String,
  domesticOnly:            Boolean,
  filingMember:            Boolean
)

object UPEDetails {
  given writes: OWrites[UPEDetails] = Json.writes[UPEDetails]
}

case class AddressDetails(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  postCode:     Option[String],
  countryCode:  String
)

object AddressDetails {
  given writes: OWrites[AddressDetails] = Json.writes[AddressDetails]
}

case class ContactDetails(
  name:         String,
  telephone:    Option[String],
  emailAddress: String
)

object ContactDetails {
  given writes: OWrites[ContactDetails] = Json.writes[ContactDetails]
}

case class FilingMemberDetails(
  safeId:                  String,
  organisationName:        String,
  customerIdentification1: String,
  customerIdentification2: String
)

object FilingMemberDetails {
  given writes: OWrites[FilingMemberDetails] = Json.writes[FilingMemberDetails]
}

case class AccountingPeriod(
  startDate: String,
  endDate:   String,
  dueDate:   Option[String]
)

object AccountingPeriod {
  given writes: OWrites[AccountingPeriod] = Json.writes[AccountingPeriod]
}

case class AccountStatus(
  inactive: Boolean
)

object AccountStatus {
  given writes: OWrites[AccountStatus] = Json.writes[AccountStatus]
}
