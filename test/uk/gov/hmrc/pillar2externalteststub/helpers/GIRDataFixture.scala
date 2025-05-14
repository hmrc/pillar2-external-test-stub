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

import org.bson.types.ObjectId
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.mongo.GIRSubmission

import java.time.Instant

trait GIRDataFixture extends Pillar2DataFixture {

  val validGIRRequest: GIRRequest = GIRRequest(
    accountingPeriodFrom = accountingPeriod.startDate,
    accountingPeriodTo = accountingPeriod.endDate
  )

  val validGIRRequestBody: JsValue = Json.toJson(validGIRRequest)

  val girMongoSubmission: GIRSubmission = GIRSubmission(
    _id = new ObjectId(),
    pillar2Id = validPlrId,
    accountingPeriodFrom = accountingPeriod.startDate,
    accountingPeriodTo = accountingPeriod.endDate,
    submittedAt = Instant.now()
  )

  val differentPeriodGirMongoSubmission: GIRSubmission = GIRSubmission(
    _id = new ObjectId(),
    pillar2Id = validPlrId,
    accountingPeriodFrom = accountingPeriod.startDate.plusYears(1),
    accountingPeriodTo = accountingPeriod.endDate.plusYears(1),
    submittedAt = Instant.now()
  )

  def createGIRRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(POST, "/pillar2/test/globe-information-return")
      .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  def createGIRRequestWithBody(plrId: String, request: GIRRequest): FakeRequest[JsValue] =
    createGIRRequest(plrId, Json.toJson(request))
}
