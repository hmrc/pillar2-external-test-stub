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

package uk.gov.hmrc.pillar2externalteststub.models.gir.mongo

import org.bson.types.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest

import java.time.Instant
import java.time.LocalDate

case class GIRSubmission(
  _id:                  ObjectId,
  pillar2Id:            String,
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  submittedAt:          Instant
)

object GIRSubmission {

  def fromRequest(pillar2Id: String, request: GIRRequest): GIRSubmission =
    GIRSubmission(
      _id = new ObjectId(),
      pillar2Id = pillar2Id,
      accountingPeriodFrom = request.accountingPeriodFrom,
      accountingPeriodTo = request.accountingPeriodTo,
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
  private val mongoReads: Reads[GIRSubmission] =
    (
      (__ \ "_id").read[ObjectId](MongoFormats.objectIdFormat) and
        (__ \ "pillar2Id").read[String] and
        (__ \ "accountingPeriodFrom").read[LocalDate] and
        (__ \ "accountingPeriodTo").read[LocalDate] and
        (__ \ "submittedAt").read[Instant](mongoInstantFormat)
    )(GIRSubmission.apply _)

  private val mongoWrites: OWrites[GIRSubmission] =
    (
      (__ \ "_id").write[ObjectId](MongoFormats.objectIdFormat) and
        (__ \ "pillar2Id").write[String] and
        (__ \ "accountingPeriodFrom").write[LocalDate] and
        (__ \ "accountingPeriodTo").write[LocalDate] and
        (__ \ "submittedAt").write[Instant](mongoInstantFormat)
    )(submission => (submission._id, submission.pillar2Id, submission.accountingPeriodFrom, submission.accountingPeriodTo, submission.submittedAt))

  val mongoFormat: OFormat[GIRSubmission] = OFormat(mongoReads, mongoWrites)
}
