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

import java.time.LocalDate

case class SuccessResponse(
                            success: SuccessDetails
                          )

case class SuccessDetails(
                           formBundleNumber: String,
                           upeDetails: UpeDetails,
                           upeCorrespAddressDetails: AddressDetails,
                           primaryContactDetails: ContactDetails,
                           secondaryContactDetails: Option[ContactDetails],
                           filingMemberDetails: Option[FilingMemberDetails],
                           accountingPeriod: AccountingPeriod,
                           accountStatus: AccountStatus
                         )

case class UpeDetails(
                       customerIdentification1: Option[String],
                       customerIdentification2: Option[String],
                       organisationName: String,
                       registrationDate: LocalDate,
                       domesticOnly: Boolean,
                       filingMember: Boolean
                     )

case class AddressDetails(
                           addressLine1: String,
                           addressLine2: Option[String],
                           addressLine3: Option[String],
                           addressLine4: Option[String],
                           postCode: Option[String],
                           countryCode: String // Should be validated against the ISO 3166-1 alpha-2 codes
                         )

case class ContactDetails(
                           name: String,
                           telephone: Option[String],
                           emailAddress: String
                         )

case class FilingMemberDetails(
                                safeId: String,
                                organisationName: String,
                                customerIdentification1: Option[String],
                                customerIdentification2: Option[String]
                              )

case class AccountingPeriod(
                             startDate: LocalDate,
                             endDate: LocalDate,
                             dueDate: Option[LocalDate]
                           )

case class AccountStatus(
                          inactive: Boolean
                        )