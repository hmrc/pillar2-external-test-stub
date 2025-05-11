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
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions._
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{UKTRLiabilityReturn, UKTRNilReturn}

import java.time.{Instant, LocalDate}

case class ObligationsAndSubmissionsMongoSubmission(
  _id:              ObjectId,
  submissionId:     ObjectId,
  pillar2Id:        String,
  accountingPeriod: AccountingPeriod,
  submissionType:   SubmissionType,
  ornCountryGir:    Option[String],
  submittedAt:      Instant
)

case class AccountingPeriod(startDate: LocalDate, endDate: LocalDate)

object AccountingPeriod {
  implicit val format: Format[AccountingPeriod] = Json.format[AccountingPeriod]
}

object ObligationsAndSubmissionsMongoSubmission {

  def fromRequest(
    pillar2Id:   String,
    submission:  BaseSubmission,
    id:          ObjectId,
    isAmendment: Boolean = false
  ): ObligationsAndSubmissionsMongoSubmission = {
    val submissionType = submission match {
      case _: UKTRNilReturn | _: UKTRLiabilityReturn => if (isAmendment) SubmissionType.UKTR_AMEND else SubmissionType.UKTR_CREATE
      case _: BTNRequest => SubmissionType.BTN
      case _: ORNRequest => if (isAmendment) SubmissionType.ORN_AMEND else SubmissionType.ORN_CREATE
      case _: GIRRequest => SubmissionType.GIR_CREATE
      case _ => throw new IllegalArgumentException("Unsupported submission type")
    }

    val ornCountryGir = submission match {
      case request: ORNRequest => Option(request.countryGIR)
      case _ => None
    }

    ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId(),
      submissionId = id,
      pillar2Id = pillar2Id,
      accountingPeriod = AccountingPeriod(submission.accountingPeriodFrom, submission.accountingPeriodTo),
      submissionType = submissionType,
      ornCountryGir = ornCountryGir,
      submittedAt = Instant.now()
    )
  }

  implicit val format: OFormat[ObligationsAndSubmissionsMongoSubmission] = Json.format[ObligationsAndSubmissionsMongoSubmission]

}
