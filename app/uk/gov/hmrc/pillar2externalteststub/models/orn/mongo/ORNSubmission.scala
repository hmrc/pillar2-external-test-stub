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

package uk.gov.hmrc.pillar2externalteststub.models.orn.mongo

import org.bson.types.ObjectId
import play.api.libs.functional.syntax.*
import play.api.libs.json.*
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest

import java.time.Instant
import java.time.LocalDate

case class ORNSubmission(
  _id:                  ObjectId,
  pillar2Id:            String,
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  filedDateGIR:         LocalDate,
  countryGIR:           String,
  reportingEntityName:  String,
  TIN:                  String,
  issuingCountryTIN:    String,
  submittedAt:          Instant
)

object ORNSubmission {

  def fromRequest(pillar2Id: String, request: ORNRequest): ORNSubmission =
    ORNSubmission(
      _id = new ObjectId(),
      pillar2Id = pillar2Id,
      accountingPeriodFrom = request.accountingPeriodFrom,
      accountingPeriodTo = request.accountingPeriodTo,
      filedDateGIR = request.filedDateGIR,
      countryGIR = request.countryGIR,
      reportingEntityName = request.reportingEntityName,
      TIN = request.TIN,
      issuingCountryTIN = request.issuingCountryTIN,
      submittedAt = Instant.now()
    )

  private val mongoInstantFormat: Format[Instant] = new Format[Instant] {

    override def writes(instant: Instant): JsValue = Json.obj(
      "$date" -> Json.obj(
        "$numberLong" -> instant.toEpochMilli.toString
      )
    )

    override def reads(json: JsValue): JsResult[Instant] = json match {
      case obj: JsObject if (obj \ "$date" \ "$numberLong").isDefined =>
        (obj \ "$date" \ "$numberLong").get.validate[String].map(s => Instant.ofEpochMilli(s.toLong))
      case _ => JsError("Expected MongoDB date format")
    }
  }

  // MongoDB format for storage
  private val mongoReads: Reads[ORNSubmission] =
    (
      (__ \ "_id").read[ObjectId](MongoFormats.objectIdFormat) and
        (__ \ "pillar2Id").read[String] and
        (__ \ "accountingPeriodFrom").read[LocalDate] and
        (__ \ "accountingPeriodTo").read[LocalDate] and
        (__ \ "filedDateGIR").read[LocalDate] and
        (__ \ "countryGIR").read[String] and
        (__ \ "reportingEntityName").read[String] and
        (__ \ "TIN").read[String] and
        (__ \ "issuingCountryTIN").read[String] and
        (__ \ "submittedAt").read[Instant](mongoInstantFormat)
    )(ORNSubmission.apply)

  private val mongoWrites: OWrites[ORNSubmission] =
    (
      (__ \ "_id").write[ObjectId](MongoFormats.objectIdFormat) and
        (__ \ "pillar2Id").write[String] and
        (__ \ "accountingPeriodFrom").write[LocalDate] and
        (__ \ "accountingPeriodTo").write[LocalDate] and
        (__ \ "filedDateGIR").write[LocalDate] and
        (__ \ "countryGIR").write[String] and
        (__ \ "reportingEntityName").write[String] and
        (__ \ "TIN").write[String] and
        (__ \ "issuingCountryTIN").write[String] and
        (__ \ "submittedAt").write[Instant](mongoInstantFormat)
    )(submission =>
      (
        submission._id,
        submission.pillar2Id,
        submission.accountingPeriodFrom,
        submission.accountingPeriodTo,
        submission.filedDateGIR,
        submission.countryGIR,
        submission.reportingEntityName,
        submission.TIN,
        submission.issuingCountryTIN,
        submission.submittedAt
      )
    )

  val mongoFormat: OFormat[ORNSubmission] = OFormat(mongoReads, mongoWrites)
}
