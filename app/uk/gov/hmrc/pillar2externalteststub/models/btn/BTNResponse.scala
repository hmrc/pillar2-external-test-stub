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

package uk.gov.hmrc.pillar2externalteststub.models.btn

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNResponse.now

import java.time.temporal.ChronoUnit
import java.time.{ZoneOffset, ZonedDateTime}

trait BTNResponse

object BTNResponse {
  def now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
}

case class BTNSuccessResponse(success: BTNSuccess) extends BTNResponse

object BTNSuccessResponse {
  implicit val format: OFormat[BTNSuccessResponse] = Json.format[BTNSuccessResponse]

  def BTN_SUCCESS_201: BTNSuccessResponse = BTNSuccessResponse(
    BTNSuccess(
      processingDate = now
    )
  )
}

case class BTNSuccess(processingDate: ZonedDateTime)

object BTNSuccess {
  implicit val format: OFormat[BTNSuccess] = Json.format[BTNSuccess]
}

case class BTNErrorResponse(error: BTNError) extends BTNResponse

object BTNErrorResponse {
  implicit val format: OFormat[BTNErrorResponse] = Json.format[BTNErrorResponse]

  def BTN_ERROR_400(message: String = "Bad request"): BTNErrorResponse = BTNErrorResponse(
    BTNError(
      code = "400",
      message = message,
      logID = "C0000000000000000000000000000400"
    )
  )

  def BTN_ERROR_500(message: String = "Internal server error"): BTNErrorResponse = BTNErrorResponse(
    BTNError(
      code = "500",
      message = message,
      logID = "C0000000000000000000000000000500"
    )
  )
}

case class BTNError(code: String, message: String, logID: String)

object BTNError {
  implicit val format: OFormat[BTNError] = Json.format[BTNError]
}

case class BTNFailureResponse(errors: BTNFailure) extends BTNResponse

object BTNFailureResponse {
  implicit val format: OFormat[BTNFailureResponse] = Json.format[BTNFailureResponse]

  def BTN_PILLAR2_MISSING_OR_INVALID_002: BTNFailureResponse = BTNFailureResponse(
    BTNFailure(
      processingDate = now,
      code = "002",
      text = "Pillar2 ID is missing or invalid"
    )
  )

  def BTN_REQUEST_INVALID_003: BTNFailureResponse = BTNFailureResponse(
    BTNFailure(
      processingDate = now,
      code = "003",
      text = "Request could not be processed or invalid"
    )
  )

  def BTN_DUPLICATE_SUBMISSION_004: BTNFailureResponse = BTNFailureResponse(
    BTNFailure(
      processingDate = now,
      code = "004",
      text = "Duplicate Submission"
    )
  )

  def BTN_BUSINESS_PARTNER_NOT_ACTIVE_007: BTNFailureResponse = BTNFailureResponse(
    BTNFailure(
      processingDate = now,
      code = "007",
      text = "Business Partner does not have an Active Pillar 2 registration"
    )
  )

  def BTN_TAX_OBLIGATION_FULFILLED_044: BTNFailureResponse = BTNFailureResponse(
    BTNFailure(
      processingDate = now,
      code = "044",
      text = "Tax Obligation already fulfilled"
    )
  )
}

case class BTNFailure(processingDate: ZonedDateTime, code: String, text: String)

object BTNFailure {
  implicit val format: OFormat[BTNFailure] = Json.format[BTNFailure]
}
