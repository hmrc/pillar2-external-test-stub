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
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.{Instant, LocalDate}

case class OrgDetails(
  domesticOnly: Boolean,
  organisationName: String,
  registrationDate: LocalDate
)

case class AccountingPeriod(
  startDate: LocalDate,
  endDate: LocalDate,
  duetDate: LocalDate
)

case class OrganisationDetails(
  orgDetails: OrgDetails,
  accountingPeriod: AccountingPeriod,
  lastUpdated: Instant = Instant.now()
) {
  def withPillar2Id(pillar2Id: String): OrganisationDetailsWithId =
    OrganisationDetailsWithId(pillar2Id, this)
}

case class OrganisationDetailsWithId(
  pillar2Id: String,
  details: OrganisationDetails
)

object OrgDetails {
  implicit val format: Format[OrgDetails] = Json.format[OrgDetails]
}

object AccountingPeriod {
  implicit val format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

object OrganisationDetails {
  val reads: Reads[OrganisationDetails] = {
    (
      (__ \ "orgDetails").read[OrgDetails] and
      (__ \ "accountingPeriod").read[AccountingPeriod] and
      (__ \ "lastUpdated").read(MongoJavatimeFormats.instantFormat)
    )(OrganisationDetails.apply _)
  }

  val writes: OWrites[OrganisationDetails] = {
    (
      (__ \ "orgDetails").write[OrgDetails] and
      (__ \ "accountingPeriod").write[AccountingPeriod] and
      (__ \ "lastUpdated").write(MongoJavatimeFormats.instantFormat)
    )(unlift(OrganisationDetails.unapply))
  }

  implicit val format: OFormat[OrganisationDetails] = OFormat(reads, writes)
}

object OrganisationDetailsWithId {
  implicit val format: Format[OrganisationDetailsWithId] = Json.format[OrganisationDetailsWithId]
} 