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

object BadRequestInvalidCorrelationID {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("INVALID_CORRELATIONID", "Submission has not passed validation. Invalid Header CorrelationId.")
    )
  )
}

object BadRequestInvalidOrPillar2Reference {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("INVALID_ID", "The backend has indicated that the supplied ID (PLR Reference) is invalid."),
      Failure("INVALID_PLR_REFERENCE", "Submission has not passed validation. Invalid path parameter: plrReference.")
    )
  )
}

object NotFoundSubscription {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("SUBSCRIPTION_NOT_FOUND", "The backend has indicated that no subscription data has been found.")
    )
  )
}

object DuplicateRecord422 {
  val response: ErrorResponse = ErrorResponse(
    Seq(
      Failure("CANNOT_COMPLETE_REQUEST", "Request could not be completed because the subscription is being created or amended."),
      Failure("REQUEST_NOT_PROCESSED", "The backend has indicated that the request could not be processed.")
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
