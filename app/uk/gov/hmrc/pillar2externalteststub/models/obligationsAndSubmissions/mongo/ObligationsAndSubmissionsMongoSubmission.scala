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

package uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo

import org.bson.types.ObjectId
import play.api.libs.json._
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats.Implicits._
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats.Implicits._
import uk.gov.hmrc.pillar2externalteststub.models.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn}

import java.time.{Instant, LocalDate}

case class ObligationsAndSubmissionsMongoSubmission(
  _id:              ObjectId,
  submissionId:     ObjectId,
  pillar2Id:        String,
  accountingPeriod: AccountingPeriod,
  submissionType:   SubmissionType,
  submittedAt:      Instant
)

case class AccountingPeriod(startDate: LocalDate, endDate: LocalDate)

object AccountingPeriod {
  implicit val format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

object ObligationsAndSubmissionsMongoSubmission {

  def fromRequest(pillar2Id: String, submission: BaseSubmission, id: ObjectId): ObligationsAndSubmissionsMongoSubmission = {
    val submissionType = submission match {
      case _: UKTRNilReturn | _: UKTRLiabilityReturn => SubmissionType.UKTR
      case _: BTNRequest => SubmissionType.BTN
      case _ => throw new IllegalArgumentException("Unsupported submission type")
    }

    ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId(),
      submissionId = id,
      pillar2Id = pillar2Id,
      accountingPeriod = AccountingPeriod(submission.accountingPeriodFrom, submission.accountingPeriodTo),
      submissionType = submissionType,
      submittedAt = Instant.now()
    )
  }

  implicit val format: OFormat[ObligationsAndSubmissionsMongoSubmission] = Json.format[ObligationsAndSubmissionsMongoSubmission]

}
