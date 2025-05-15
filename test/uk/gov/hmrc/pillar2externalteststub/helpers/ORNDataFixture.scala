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
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{GET, POST, PUT}
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.orn.mongo.ORNSubmission

import java.time.{Instant, LocalDate}

trait ORNDataFixture extends Pillar2DataFixture {

  val validORNRequest: ORNRequest = ORNRequest(
    accountingPeriodFrom = accountingPeriod.startDate,
    accountingPeriodTo = accountingPeriod.endDate,
    filedDateGIR = LocalDate.of(2025, 1, 10),
    countryGIR = "US",
    reportingEntityName = "Newco PLC",
    TIN = "US12345678",
    issuingCountryTIN = "US"
  )

  val validRequestBody: JsValue = Json.toJson(validORNRequest)

  val ornMongoSubmission: ORNSubmission = ORNSubmission(
    _id = new ObjectId(),
    pillar2Id = validPlrId,
    accountingPeriodFrom = validORNRequest.accountingPeriodFrom,
    accountingPeriodTo = validORNRequest.accountingPeriodTo,
    filedDateGIR = validORNRequest.filedDateGIR,
    countryGIR = validORNRequest.countryGIR,
    reportingEntityName = validORNRequest.reportingEntityName,
    TIN = validORNRequest.TIN,
    issuingCountryTIN = validORNRequest.issuingCountryTIN,
    submittedAt = Instant.now()
  )

  def createSubmitRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(POST, "/RESTAdapter/plr/overseas-return-notification")
      .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> plrId): _*)
      .withBody(body)

  def createAmendRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(PUT, "/RESTAdapter/plr/overseas-return-notification")
      .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> plrId): _*)
      .withBody(body)

  def createRequestWithBody(plrId: String, request: ORNRequest, isAmend: Boolean = false): FakeRequest[JsValue] =
    if (isAmend) createAmendRequest(plrId, Json.toJson(request))
    else createSubmitRequest(plrId, Json.toJson(request))

  def getORNRequest(plrId: String, fromDate: String, toDate: String): FakeRequest[AnyContentAsEmpty.type] =
    FakeRequest(GET, s"/RESTAdapter/plr/overseas-return-notification?accountingPeriodFrom=$fromDate&accountingPeriodTo=$toDate")
      .withHeaders(hipHeaders :+ ("X-Pillar2-Id" -> plrId): _*)
}
