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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.HeaderNames
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers.POST
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest

import java.time.LocalDate

trait BTNDataFixture extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite {

  val authHeader: (String, String) = HeaderNames.AUTHORIZATION -> "Bearer token"

  val validPlrId       = "XMPLR0000000000"
  val serverErrorPlrId = "XEPLR0000000500"

  val validRequest: BTNRequest = BTNRequest(
    accountingPeriodFrom = LocalDate.of(2024, 1, 1),
    accountingPeriodTo = LocalDate.of(2024, 12, 31)
  )

  val validRequestBody: JsValue = Json.toJson(validRequest)

  def createRequest(plrId: String, body: JsValue): FakeRequest[JsValue] =
    FakeRequest(POST, "/RESTAdapter/PLR/below-threshold-notification")
      .withHeaders(
        HeaderNames.CONTENT_TYPE -> "application/json",
        authHeader,
        "X-Pillar2-Id" -> plrId
      )
      .withBody(body)

  def createRequestWithBody(plrId: String, request: BTNRequest): FakeRequest[JsValue] =
    createRequest(plrId, Json.toJson(request))
}
