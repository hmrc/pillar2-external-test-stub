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
import uk.gov.hmrc.pillar2externalteststub.models.mongo.instantFormat
case class OrgDetails(
  domesticOnly:     Boolean,
  organisationName: String,
  registrationDate: LocalDate
)

case class AccountingPeriod(
  startDate: LocalDate,
  endDate:   LocalDate
)

case class TestOrganisationRequest(
  orgDetails:       OrgDetails,
  accountingPeriod: AccountingPeriod
)

case class TestOrganisation(
  orgDetails:       OrgDetails,
  accountingPeriod: AccountingPeriod,
  lastUpdated:      Instant = Instant.now()
) {
  def withPillar2Id(pillar2Id: String): TestOrganisationWithId =
    TestOrganisationWithId(pillar2Id, this)
}

case class TestOrganisationWithId(
  pillar2Id:    String,
  organisation: TestOrganisation
)

object OrgDetails {
  implicit val format: Format[OrgDetails] = Json.format[OrgDetails]
}

object AccountingPeriod {
  implicit val format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

object TestOrganisationRequest {
  implicit val format: Format[TestOrganisationRequest] = Json.format[TestOrganisationRequest]
}

object TestOrganisation {
  private val dateTimeFormatter = DateTimeFormatter.ISO_INSTANT

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
      accountingPeriod = request.accountingPeriod
    )

  private val mongoReads: Reads[TestOrganisation] =
    (
      (__ \ "orgDetails").read[OrgDetails] and
        (__ \ "accountingPeriod").read[AccountingPeriod] and
        (__ \ "lastUpdated").read[Instant](instantFormat)
    )(TestOrganisation.apply _)

  private val mongoWrites: OWrites[TestOrganisation] =
    (
      (__ \ "orgDetails").write[OrgDetails] and
        (__ \ "accountingPeriod").write[AccountingPeriod] and
        (__ \ "lastUpdated").write(instantFormat)
    )(unlift(TestOrganisation.unapply))

  val mongoFormat: OFormat[TestOrganisation] = OFormat(mongoReads, mongoWrites)

  private val apiReads: Reads[TestOrganisation] =
    (
      (__ \ "orgDetails").read[OrgDetails] and
        (__ \ "accountingPeriod").read[AccountingPeriod] and
        (__ \ "lastUpdated").read[Instant](apiInstantFormat)
    )(TestOrganisation.apply _)

  private val apiWrites: OWrites[TestOrganisation] =
    (
      (__ \ "orgDetails").write[OrgDetails] and
        (__ \ "accountingPeriod").write[AccountingPeriod] and
        (__ \ "lastUpdated").write(apiInstantFormat)
    )(unlift(TestOrganisation.unapply))

  implicit val format: OFormat[TestOrganisation] = OFormat(apiReads, apiWrites)
}

object TestOrganisationWithId {
  implicit val format: Format[TestOrganisationWithId] = Json.format[TestOrganisationWithId]
}
