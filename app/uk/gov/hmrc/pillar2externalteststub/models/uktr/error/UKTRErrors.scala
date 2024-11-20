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

package uk.gov.hmrc.pillar2externalteststub.models.uktr.error

import play.api.libs.json.{Json, OFormat}

case class UKTRError(
  code: String,
  message: String,
  logID: Option[String]
)

object UKTRError {
  implicit val format: OFormat[UKTRError] = Json.format[UKTRError]
}

case class UKTRErrorDetail(
  processingDate: String,
  code: String,
  text: String
)

object UKTRErrorDetail {
  implicit val format: OFormat[UKTRErrorDetail] = Json.format[UKTRErrorDetail]
}

// Error response objects
object ValidationError422 {
  val response: UKTRErrorDetail = UKTRErrorDetail(
    processingDate = "2022-01-31T09:26:17Z",
    code = "001",
    text = "REGIME missing or invalid"
  )
}

object SAPError500 {
  val response: UKTRError = UKTRError(
    code = "500",
    message = "Error while sending message to module processor: System Error Received. HTTP Status Code = 200; ErrorCode = INCORRECT_PAYLOAD_DATA; Additional text = Error while processing message payload",
    logID = Some("C0000AB8190C8E1F000000C700006836")
  )
}

object InvalidJsonError400 {
  val response: UKTRError = UKTRError(
    code = "400",
    message = "Invalid JSON message content used; Message: \"Expected a ',' or '}' at character 93...\"",
    logID = Some("C0000AB8190C86300000000200006836")
  )
} 