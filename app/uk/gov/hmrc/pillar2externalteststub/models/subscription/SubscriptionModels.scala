/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json._

case class UPEDetails(
  customerIdentification1: String,
  customerIdentification2: String,
  organisationName:        String,
  registrationDate:        String,
  domesticOnly:            Boolean,
  filingMember:            Boolean
)

object UPEDetails {
  implicit val format: OFormat[UPEDetails] = Json.format[UPEDetails]
}

case class AddressDetails(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  postCode:     Option[String],
  countryCode:  String
)

object AddressDetails {
  implicit val format: OFormat[AddressDetails] = Json.format[AddressDetails]
}

case class ContactDetails(
  name:         String,
  telephone:    Option[String],
  emailAddress: String
)

object ContactDetails {
  implicit val format: OFormat[ContactDetails] = Json.format[ContactDetails]
}

case class FilingMemberDetails(
  customerIdentification1: String,
  customerIdentification2: String,
  organisationName:        String,
  registrationDate:        String,
  domesticOnly:            Boolean,
  filingMember:            Boolean
)

object FilingMemberDetails {
  implicit val format: OFormat[FilingMemberDetails] = Json.format[FilingMemberDetails]
}

case class AccountingPeriod(
  startDate: String,
  endDate:   String
)

object AccountingPeriod {
  implicit val format: OFormat[AccountingPeriod] = Json.format[AccountingPeriod]
}

case class AccountStatus(
  status:          String,
  statusStartDate: String,
  statusEndDate:   Option[String]
)

object AccountStatus {
  implicit val format: OFormat[AccountStatus] = Json.format[AccountStatus]
}

case class SubscriptionMongo(
  plrReference:            String,
  upeDetails:              UPEDetails,
  addressDetails:          AddressDetails,
  contactDetails:          ContactDetails,
  secondaryContactDetails: Option[ContactDetails],
  filingMemberDetails:     FilingMemberDetails,
  accountingPeriod:        AccountingPeriod,
  accountStatus:           AccountStatus
)

object SubscriptionMongo {
  implicit val format: OFormat[SubscriptionMongo] = Json.format[SubscriptionMongo]
}

case class Subscription(
  plrReference:            String,
  upeDetails:              UPEDetails,
  addressDetails:          AddressDetails,
  contactDetails:          ContactDetails,
  secondaryContactDetails: Option[ContactDetails],
  filingMemberDetails:     FilingMemberDetails,
  accountingPeriod:        AccountingPeriod,
  accountStatus:           AccountStatus
)

object Subscription {
  implicit val format: OFormat[Subscription] = Json.format[Subscription]
}
