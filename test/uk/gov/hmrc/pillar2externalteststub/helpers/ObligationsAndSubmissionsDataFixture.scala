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

package uk.gov.hmrc.pillar2externalteststub.helpers
import org.mongodb.scala.bson.ObjectId
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.{AccountingPeriod, ObligationsAndSubmissionsMongoSubmission}

import java.time.Instant

trait ObligationsAndSubmissionsDataFixture extends Pillar2DataFixture {

  def generateObligationsAndSubmissionsMongoSubmission(subtype: SubmissionType): ObligationsAndSubmissionsMongoSubmission =
    ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId(),
      submissionId = new ObjectId(),
      pillar2Id = validPlrId,
      accountingPeriod = AccountingPeriod(accountingPeriod.startDate, accountingPeriod.endDate),
      submissionType = subtype,
      ornCountryGir = if (subtype == ORN_CREATE || subtype == ORN_AMEND) Some("US") else None,
      submittedAt = Instant.now()
    )

  def uktrObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    generateObligationsAndSubmissionsMongoSubmission(UKTR_CREATE)

  def ornObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    generateObligationsAndSubmissionsMongoSubmission(ORN_CREATE)

  def olderBtnObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId(),
      submissionId = new ObjectId(),
      pillar2Id = validPlrId,
      accountingPeriod = AccountingPeriod(accountingPeriod.startDate, accountingPeriod.endDate),
      submissionType = BTN,
      ornCountryGir = None,
      submittedAt = Instant.now().minusSeconds(3600)
    )

  def differentPeriodBtnObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId(),
      submissionId = new ObjectId(),
      pillar2Id = validPlrId,
      accountingPeriod = AccountingPeriod(
        accountingPeriod.startDate.plusYears(1),
        accountingPeriod.endDate.plusYears(1)
      ),
      submissionType = BTN,
      ornCountryGir = None,
      submittedAt = Instant.now()
    )

  def uktrAmendObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    generateObligationsAndSubmissionsMongoSubmission(UKTR_AMEND)

  def ornAmendObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    generateObligationsAndSubmissionsMongoSubmission(ORN_AMEND)

  val girCreateObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    generateObligationsAndSubmissionsMongoSubmission(GIR_CREATE)

  val differentPeriodGirObligationsAndSubmissionsMongoSubmission: ObligationsAndSubmissionsMongoSubmission =
    ObligationsAndSubmissionsMongoSubmission(
      _id = new ObjectId(),
      submissionId = new ObjectId(),
      pillar2Id = validPlrId,
      accountingPeriod = AccountingPeriod(accountingPeriod.startDate.plusYears(1), accountingPeriod.endDate.plusYears(1)),
      submissionType = GIR_CREATE,
      ornCountryGir = None,
      submittedAt = Instant.now()
    )
}
