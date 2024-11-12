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

package uk.gov.hmrc.pillar2externalteststub.models

import play.api.libs.json.{Json, OWrites}

case class Failure(code: String, reason: String)

object Failure {
  implicit val writes: OWrites[Failure] = Json.writes[Failure]
}

case class ErrorResponse(failures: Seq[Failure])

object ErrorResponse {
  implicit val writes: OWrites[ErrorResponse] = Json.writes[ErrorResponse]
}

object NotFoundSubscription {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("SUBSCRIPTION_NOT_FOUND", "The backend has indicated that no subscription data has been found.")
    )
  )
}

object ServerError500 {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("SERVER_ERROR", "IF is currently experiencing problems that require live service intervention.")
    )
  )
}

object ServiceUnavailable503 {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("SERVICE_UNAVAILABLE", "Dependent systems are currently not responding.")
    )
  )
}

case class UKTRError(
  code:    String,
  message: String,
  logID:   Option[String]
)

object UKTRError {
  implicit val writes: OWrites[UKTRError] = Json.writes[UKTRError]
}

case class UKTRErrorDetail(
  processingDate: String,
  code:           String,
  text:           String
)

object UKTRErrorDetail {
  implicit val writes: OWrites[UKTRErrorDetail] = Json.writes[UKTRErrorDetail]
}

// Specific error responses for scenarios

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
    message =
      "Error while sending message to module processor: System Error Received. HTTP Status Code = 200; ErrorCode = INCORRECT_PAYLOAD_DATA; Additional text = Error while processing message payload",
    logID = Some("C0000AB8190C8E1F000000C700006836")
  )
}

object InvalidJsonError400 {
  val response: UKTRError = UKTRError(
    code = "400",
    message = "Invalid JSON message content used; Message: \"Expected a ',' or '}' at character 93...",
    logID = Some("C0000AB8190C86300000000200006836")
  )
}

object BadRequestInvalidRequestBody {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("INVALID_REQUEST_BODY", "The request body could not be parsed.")
    )
  )
}
