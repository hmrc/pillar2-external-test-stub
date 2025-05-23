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

package uk.gov.hmrc.pillar2externalteststub.models.subscription

import play.api.libs.json.{Json, OFormat}

case class Failure(code: String, reason: String)

object Failure {
  implicit val format: OFormat[Failure] = Json.format[Failure]
}

case class ErrorResponse(failures: Seq[Failure]) extends SubscriptionResponse

object ErrorResponse {
  implicit val format: OFormat[ErrorResponse] = Json.format[ErrorResponse]
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
