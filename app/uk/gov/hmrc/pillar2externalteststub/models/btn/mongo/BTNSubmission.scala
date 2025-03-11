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

package uk.gov.hmrc.pillar2externalteststub.models.btn.mongo

import org.bson.types.ObjectId
import play.api.libs.functional.syntax._
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.mongo.instantFormat
import java.time.Instant
import java.time.LocalDate

case class BTNSubmission(
  _id:                  ObjectId,
  pillar2Id:            String,
  accountingPeriodFrom: LocalDate,
  accountingPeriodTo:   LocalDate,
  submittedAt:          Instant
)

object BTNSubmission {

  def fromRequest(pillar2Id: String, request: BTNRequest): BTNSubmission =
    BTNSubmission(
      _id = new ObjectId(),
      pillar2Id = pillar2Id,
      accountingPeriodFrom = request.accountingPeriodFrom,
      accountingPeriodTo = request.accountingPeriodTo,
      submittedAt = Instant.now()
    )

  // MongoDB format for storage
  private val mongoReads: Reads[BTNSubmission] =
    (
      (__ \ "_id").read[ObjectId](MongoFormats.objectIdFormat) and
        (__ \ "pillar2Id").read[String] and
        (__ \ "accountingPeriodFrom").read[LocalDate] and
        (__ \ "accountingPeriodTo").read[LocalDate] and
        (__ \ "submittedAt").read[Instant](instantFormat)
    )(BTNSubmission.apply _)

  private val mongoWrites: OWrites[BTNSubmission] =
    (
      (__ \ "_id").write[ObjectId](MongoFormats.objectIdFormat) and
        (__ \ "pillar2Id").write[String] and
        (__ \ "accountingPeriodFrom").write[LocalDate] and
        (__ \ "accountingPeriodTo").write[LocalDate] and
        (__ \ "submittedAt").write[Instant](instantFormat)
    )(unlift(BTNSubmission.unapply))

  val mongoFormat: OFormat[BTNSubmission] = OFormat(mongoReads, mongoWrites)
}
