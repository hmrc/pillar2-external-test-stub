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

case class SubscriptionSuccessResponse(
  formBundleNumber:         String,
  upeDetails:               UPEDetails,
  upeCorrespAddressDetails: AddressDetails,
  primaryContactDetails:    ContactDetails,
  secondaryContactDetails:  Option[ContactDetails],
  filingMemberDetails:      FilingMemberDetails,
  accountingPeriod:         AccountingPeriod,
  accountStatus:            AccountStatus
) extends SubscriptionResponse

object SubscriptionSuccessResponse {
  implicit val writes: OWrites[SubscriptionSuccessResponse] = Json.writes[SubscriptionSuccessResponse]

  def successfulDomesticOnlyResponse: SubscriptionSuccessResponse =
    SubscriptionSuccessResponse(
      formBundleNumber = "123456789123",
      upeDetails = UPEDetails(
        customerIdentification1 = "12345678",
        customerIdentification2 = "12345678",
        organisationName = "Domestic Organisation Inc.",
        registrationDate = "2022-01-31",
        domesticOnly = true,
        filingMember = true
      ),
      upeCorrespAddressDetails = AddressDetails(
        addressLine1 = "1 High Street",
        addressLine2 = Some("Egham"),
        addressLine3 = Some("Surrey"),
        postCode = Some("HP13 6TT"),
        countryCode = "GB"
      ),
      primaryContactDetails = ContactDetails(
        name = "John Doe",
        telephone = Some("0115 9700 700"),
        emailAddress = "johndoe@example.com"
      ),
      secondaryContactDetails = Some(
        ContactDetails(
          name = "Jane Doe",
          telephone = Some("0115 9700 800"),
          emailAddress = "janedoe@example.com"
        )
      ),
      filingMemberDetails = FilingMemberDetails(
        safeId = "XE0000123456789",
        organisationName = "Domestic Operations Ltd",
        customerIdentification1 = "1234Z678",
        customerIdentification2 = "1234567Y"
      ),
      accountingPeriod = AccountingPeriod(
        startDate = "2023-04-06",
        endDate = "2024-04-05",
        dueDate = Some("2024-06-30")
      ),
      accountStatus = AccountStatus(
        inactive = false
      )
    )

  def successfulNonDomesticResponse: SubscriptionSuccessResponse =
    SubscriptionSuccessResponse(
      formBundleNumber = "123456789123",
      upeDetails = UPEDetails(
        customerIdentification1 = "87654321",
        customerIdentification2 = "87654321",
        organisationName = "International Organisation Inc.",
        registrationDate = "2022-01-31",
        domesticOnly = false,
        filingMember = false
      ),
      upeCorrespAddressDetails = AddressDetails(
        addressLine1 = "123 Overseas Road",
        addressLine2 = Some("Egham"),
        addressLine3 = Some("Surrey"),
        postCode = Some("HP13 6TT"),
        countryCode = "US"
      ),
      primaryContactDetails = ContactDetails(
        name = "Fred Flintstone",
        telephone = Some("0115 9700 700"),
        emailAddress = "fred@example.com"
      ),
      secondaryContactDetails = None,
      filingMemberDetails = FilingMemberDetails(
        safeId = "XE0000987654321",
        organisationName = "International Operations Ltd",
        customerIdentification1 = "8765X432",
        customerIdentification2 = "8765432Z"
      ),
      accountingPeriod = AccountingPeriod(
        startDate = "2023-04-06",
        endDate = "2024-04-05",
        dueDate = Some("2024-06-30")
      ),
      accountStatus = AccountStatus(
        inactive = false
      )
    )
}
