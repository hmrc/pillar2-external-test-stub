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

package uk.gov.hmrc.pillar2externalteststub.models.uktr

import play.api.libs.json.{Json, OWrites}

import java.time.LocalDateTime

// Case class to represent a successful subscription retrieval response
case class SubmitUKTRSuccessResponse(
  processingDate:   LocalDateTime,
  formBundleNumber: String,
  chargeReference:  Option[String]
)

object SubmitUKTRSuccessResponse {
  implicit val writes: OWrites[SubmitUKTRSuccessResponse] = Json.writes[SubmitUKTRSuccessResponse]

  def successfulDomesticOnlyResponse(): SubmitUKTRSuccessResponse =
    SubmitUKTRSuccessResponse(
      processingDate = LocalDateTime.now(),
      formBundleNumber = "123456789123",
      chargeReference = Some("XTC01234123412")
    )
}
