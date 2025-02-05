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

import play.api.libs.functional.syntax._
import play.api.libs.json._

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDate}

case class OrgDetails(
  domesticOnly:     Boolean,
  organisationName: String,
  registrationDate: LocalDate
)

case class AccountingPeriod(
  startDate: LocalDate,
  endDate:   LocalDate,
  duetDate:  LocalDate
)

// Request model without lastUpdated
case class OrganisationDetailsRequest(
  orgDetails:       OrgDetails,
  accountingPeriod: AccountingPeriod
)

// Response/Storage model with lastUpdated
case class OrganisationDetails(
  orgDetails:       OrgDetails,
  accountingPeriod: AccountingPeriod,
  lastUpdated:      Instant = Instant.now()
) {
  def withPillar2Id(pillar2Id: String): OrganisationDetailsWithId =
    OrganisationDetailsWithId(pillar2Id, this)
}

case class OrganisationDetailsWithId(
  pillar2Id: String,
  details:   OrganisationDetails
)

object OrgDetails {
  implicit val format: Format[OrgDetails] = Json.format[OrgDetails]
}

object AccountingPeriod {
  implicit val format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

object OrganisationDetailsRequest {
  implicit val format: Format[OrganisationDetailsRequest] = Json.format[OrganisationDetailsRequest]
}

object OrganisationDetails {
  private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT

  // Format for MongoDB storage
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

  // Format for API responses
  private val apiInstantFormat: Format[Instant] = new Format[Instant] {
    override def reads(json: JsValue): JsResult[Instant] = json match {
      case JsString(s) => JsSuccess(Instant.from(dateTimeFormatter.parse(s)))
      case _           => JsError("Expected ISO instant format")
    }

    override def writes(instant: Instant): JsValue = JsString(dateTimeFormatter.format(instant))
  }

  def fromRequest(request: OrganisationDetailsRequest): OrganisationDetails =
    OrganisationDetails(
      orgDetails = request.orgDetails,
      accountingPeriod = request.accountingPeriod
    )

  // MongoDB format for storage
  private val mongoReads: Reads[OrganisationDetails] =
    (
      (__ \ "orgDetails").read[OrgDetails] and
        (__ \ "accountingPeriod").read[AccountingPeriod] and
        (__ \ "lastUpdated").read[Instant](mongoInstantFormat)
    )(OrganisationDetails.apply _)

  private val mongoWrites: OWrites[OrganisationDetails] =
    (
      (__ \ "orgDetails").write[OrgDetails] and
        (__ \ "accountingPeriod").write[AccountingPeriod] and
        (__ \ "lastUpdated").write(mongoInstantFormat)
    )(unlift(OrganisationDetails.unapply))

  val mongoFormat: OFormat[OrganisationDetails] = OFormat(mongoReads, mongoWrites)

  // API format for responses
  private val apiReads: Reads[OrganisationDetails] =
    (
      (__ \ "orgDetails").read[OrgDetails] and
        (__ \ "accountingPeriod").read[AccountingPeriod] and
        (__ \ "lastUpdated").read[Instant](apiInstantFormat)
    )(OrganisationDetails.apply _)

  private val apiWrites: OWrites[OrganisationDetails] =
    (
      (__ \ "orgDetails").write[OrgDetails] and
        (__ \ "accountingPeriod").write[AccountingPeriod] and
        (__ \ "lastUpdated").write(apiInstantFormat)
    )(unlift(OrganisationDetails.unapply))

  implicit val format: OFormat[OrganisationDetails] = OFormat(apiReads, apiWrites)
}

object OrganisationDetailsWithId {
  implicit val format: Format[OrganisationDetailsWithId] = Json.format[OrganisationDetailsWithId]
}
