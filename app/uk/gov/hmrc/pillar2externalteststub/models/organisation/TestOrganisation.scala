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

package uk.gov.hmrc.pillar2externalteststub.models.organisation

import play.api.libs.functional.syntax.*
import play.api.libs.json.*

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate}

case class OrgDetails(
  domesticOnly:     Boolean,
  organisationName: String,
  registrationDate: LocalDate
)

case class AccountingPeriod(startDate: LocalDate, endDate: LocalDate, underEnquiry: Option[Boolean])

case class TestData(accountActivityScenario: AccountActivityScenario)

enum AccountActivityScenario(val raw: String):
  case DTT_CHARGE extends AccountActivityScenario("DTT_CHARGE")
  case FULLY_PAID_CHARGE extends AccountActivityScenario("FULLY_PAID_CHARGE")
  case FULLY_PAID_CHARGE_WITH_SPLIT_PAYMENTS extends AccountActivityScenario("FULLY_PAID_CHARGE_WITH_SPLIT_PAYMENTS")
  case REPAYMENT_INTEREST extends AccountActivityScenario("REPAYMENT_INTEREST")
  case DTT_DETERMINATION extends AccountActivityScenario("DTT_DETERMINATION")
  case DTT_IIR_UTPR extends AccountActivityScenario("DTT_IIR_UTPR")
  case DTT_IIR_UTPR_INTEREST extends AccountActivityScenario("DTT_IIR_UTPR_INTEREST")
  case DTT_IIR_UTPR_DETERMINATION extends AccountActivityScenario("DTT_IIR_UTPR_DETERMINATION")
  case DTT_IIR_UTPR_DISCOVERY extends AccountActivityScenario("DTT_IIR_UTPR_DISCOVERY")
  case DTT_IIR_UTPR_OVERPAID_CLAIM extends AccountActivityScenario("DTT_IIR_UTPR_OVERPAID_CLAIM")
  case UKTR_DTT_UKTR_MTT_LATE_FILING_PENALTY extends AccountActivityScenario("UKTR_DTT_UKTR_MTT_LATE_FILING_PENALTY")
  case ORN_GIR_DTT_UKTR_MTT_LATE_FILING_PENALTY extends AccountActivityScenario("ORN_GIR_DTT_UKTR_MTT_LATE_FILING_PENALTY")
  case POTENTIAL_LOST_REVENUE_PENALTY extends AccountActivityScenario("POTENTIAL_LOST_REVENUE_PENALTY")
  case SCHEDULE_36_PENALTY extends AccountActivityScenario("SCHEDULE_36_PENALTY")
  case RECORD_KEEPING_PENALTY extends AccountActivityScenario("RECORD_KEEPING_PENALTY")
  case REPAYMENT_CREDIT extends AccountActivityScenario("REPAYMENT_CREDIT")
  case INTEREST_REPAYMENT_CREDIT extends AccountActivityScenario("INTEREST_REPAYMENT_CREDIT")

case class AccountStatus(
  inactive: Boolean
)

case class TestOrganisationRequest(
  orgDetails:       OrgDetails,
  accountingPeriod: AccountingPeriod,
  testData:         Option[TestData]
)

case class TestOrganisation(
  orgDetails:       OrgDetails,
  accountingPeriod: AccountingPeriod,
  testData:         Option[TestData],
  accountStatus:    AccountStatus,
  lastUpdated:      Instant = Instant.now()
) {
  def withPillar2Id(pillar2Id: String): TestOrganisationWithId =
    TestOrganisationWithId(pillar2Id, this)
}

case class TestOrganisationWithId(
  pillar2Id:    String,
  organisation: TestOrganisation
) {
  def withUnderEnquiry(org: TestOrganisationWithId): TestOrganisationWithId =
    TestOrganisationWithId(
      pillar2Id,
      org.organisation
        .copy(
          accountingPeriod = org.organisation.accountingPeriod.copy(
            underEnquiry = org.organisation.accountingPeriod.underEnquiry.fold(Some(false))(Some(_))
          )
        )
    )
}

object OrgDetails {
  given format: Format[OrgDetails] = Json.format[OrgDetails]
}

object AccountingPeriod {
  given format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

object TestData {
  given format: Format[TestData] = Json.format[TestData]
}

object AccountActivityScenario:
  given Format[AccountActivityScenario] = Format(
    Reads {
      case JsString(str) =>
        AccountActivityScenario.values.find(_.raw == str) match {
          case Some(scenario) => JsSuccess(scenario)
          case None           => JsError(s"Unknown AccountActivityScenario: $str")
        }
      case _ => JsError("Expected string for AccountActivityScenario")
    },
    Writes(v => JsString(v.raw))
  )

object AccountStatus {
  given format: Format[AccountStatus] = Json.format[AccountStatus]
}

object TestOrganisationRequest {
  given format: Format[TestOrganisationRequest] = Json.format[TestOrganisationRequest]
}

object TestOrganisation {
  private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT

  private val mongoInstantFormat: Format[Instant] = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] = json match {
      case JsString(s) => JsSuccess(Instant.from(dateTimeFormatter.parse(s)))
      case obj: JsObject if (obj \ "$date" \ "$numberLong").isDefined =>
        (obj \ "$date" \ "$numberLong").get.validate[String].map(s => Instant.ofEpochMilli(s.toLong))
      case _ => JsError("Expected ISO instant format or MongoDB date format")
    }

    override def writes(instant: Instant): JsValue = Json.obj(
      "$date" -> Json.obj(
        "$numberLong" -> instant.toEpochMilli.toString
      )
    )
  }

  private val apiInstantFormat: Format[Instant] = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] = json match {
      case JsString(s) => JsSuccess(Instant.from(dateTimeFormatter.parse(s)))
      case _           => JsError("Expected ISO instant format")
    }

    override def writes(instant: Instant): JsValue = JsString(dateTimeFormatter.format(instant))
  }

  def fromRequest(request: TestOrganisationRequest): TestOrganisation =
    TestOrganisation(
      orgDetails = request.orgDetails,
      accountingPeriod = request.accountingPeriod,
      testData = request.testData,
      //Initialise as active until we get a BTN
      accountStatus = AccountStatus(inactive = false)
    )

  private val mongoReads: Reads[TestOrganisation] =
    (
      (__ \ "orgDetails").read[OrgDetails] and
        (__ \ "accountingPeriod").read[AccountingPeriod] and
        (__ \ "testData").readNullable[TestData] and
        (__ \ "accountStatus").read[AccountStatus] and
        (__ \ "lastUpdated").read[Instant](using mongoInstantFormat)
    )(TestOrganisation.apply)

  private val mongoWrites: OWrites[TestOrganisation] =
    (
      (__ \ "orgDetails").write[OrgDetails] and
        (__ \ "accountingPeriod").write[AccountingPeriod] and
        (__ \ "testData").writeNullable[TestData] and
        (__ \ "accountStatus").write[AccountStatus] and
        (__ \ "lastUpdated").write(using mongoInstantFormat)
    )(testOrg => (testOrg.orgDetails, testOrg.accountingPeriod, testOrg.testData, testOrg.accountStatus, testOrg.lastUpdated))

  val mongoFormat: OFormat[TestOrganisation] = OFormat(mongoReads, mongoWrites)

  private val apiReads: Reads[TestOrganisation] =
    (
      (__ \ "orgDetails").read[OrgDetails] and
        (__ \ "accountingPeriod").read[AccountingPeriod] and
        (__ \ "testData").readNullable[TestData] and
        (__ \ "accountStatus").read[AccountStatus] and
        (__ \ "lastUpdated").read[Instant](using apiInstantFormat)
    )(TestOrganisation.apply)

  private val apiWrites: OWrites[TestOrganisation] =
    (
      (__ \ "orgDetails").write[OrgDetails] and
        (__ \ "accountingPeriod").write[AccountingPeriod] and
        (__ \ "testData").writeNullable[TestData] and
        (__ \ "accountStatus").write[AccountStatus] and
        (__ \ "lastUpdated").write(using apiInstantFormat)
    )(testOrg => (testOrg.orgDetails, testOrg.accountingPeriod, testOrg.testData, testOrg.accountStatus, testOrg.lastUpdated))

  given format: OFormat[TestOrganisation] = OFormat(apiReads, apiWrites)
}

object TestOrganisationWithId {
  given format: Format[TestOrganisationWithId] = Json.format[TestOrganisationWithId]
}
