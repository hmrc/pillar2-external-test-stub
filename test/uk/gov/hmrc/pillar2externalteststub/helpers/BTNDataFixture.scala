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

package uk.gov.hmrc.pillar2externalteststub.helpers

import org.bson.types.ObjectId
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.btn.mongo.BTNSubmission

import java.time.Instant

trait BTNDataFixture extends Pillar2DataFixture {

  val validBTNRequest: BTNRequest = BTNRequest(
    accountingPeriodFrom = accountingPeriod.startDate,
    accountingPeriodTo = accountingPeriod.endDate
  )

  val validBTNRequestBody: JsValue = Json.toJson(validBTNRequest)

  val BTNMongoSubmission: BTNSubmission = BTNSubmission(
    _id = new ObjectId(),
    pillar2Id = validPlrId,
    accountingPeriodFrom = validBTNRequest.accountingPeriodFrom,
    accountingPeriodTo = validBTNRequest.accountingPeriodTo,
    submittedAt = Instant.now()
  )

  def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(POST, "/RESTAdapter/plr/below-threshold-notification")
      .withHeaders(HeaderNames.CONTENT_TYPE -> "application/json", authHeader, "X-Pillar2-Id" -> plrId)
      .withBody(body)

  def createRequestWithBody(plrId: String, request: BTNRequest): FakeRequest[JsValue] =
    createRequest(plrId, Json.toJson(request))
}
