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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UktrSubmission

case class UktrError(
  code:    String,
  message: String,
  logID:   Option[String]
)

object UktrError {
  implicit val format: OFormat[UktrError] = Json.format[UktrError]
}

object UktrErrorCodes {
  val REGIME_MISSING_OR_INVALID_001                             = "001"
  val PILLAR_2_ID_MISSING_OR_INVALID_002                        = "002"
  val REQUEST_COULD_NOT_BE_PROCESSED_003                        = "003"
  val DUPLICATE_SUBMISSION_ACKNOWLEDGMENT_REFERENCE_004         = "004"
  val BUSINESS_PARTNER_DOES_NOT_HAVE_AN_ACTIVE_SUBSCRIPTION_007 = "007"
  val TAX_OBLIGATION_ALREADY_FULFILLED_044                      = "044"
  val INVALID_RETURN_093                                        = "093"
  val INVALID_DTT_ELECTION_094                                  = "094"
  val INVALID_UTPR_ELECTION_095                                 = "095"
  val INVALID_TOTAL_LIABILITY_096                               = "096"
  val INVALID_TOTAL_LIABILITY_IIR_097                           = "097"
  val INVALID_TOTAL_LIABILITY_DTT_098                           = "098"
  val INVALID_TOTAL_LIABILITY_UTPR_099                          = "099"
  val BAD_REQUEST_400                                           = "400"
  val UNPROCESSABLE_CONTENT_422                                 = "422"
  val INTERNAL_SERVER_ERROR_500                                 = "500"
}

case class UktrBusinessValidationErrorDetail(
  processingDate: String,
  code:           String,
  text:           String
)

object UktrBusinessValidationErrorDetail {
  implicit val format: OFormat[UktrBusinessValidationErrorDetail] = Json.format[UktrBusinessValidationErrorDetail]
}

object ValidationError422RegimeMissingOrInvalid {
  val response: UktrBusinessValidationErrorDetail = UktrBusinessValidationErrorDetail(
    processingDate = UktrSubmission.UKTR_STUB_PROCESSING_DATE,
    code = UktrErrorCodes.REGIME_MISSING_OR_INVALID_001,
    text = "REGIME missing or invalid"
  )
}

object SAPError500 {
  val response: UktrError = UktrError(
    code = UktrErrorCodes.INTERNAL_SERVER_ERROR_500,
    message =
      "Error while sending message to module processor: System Error Received. HTTP Status Code = 200; ErrorCode = INCORRECT_PAYLOAD_DATA; Additional text = Error while processing message payload",
    logID = Some("C0000AB8190C8E1F000000C700006836")
  )
}

object InvalidError400StaticErrorMessage {
  val response: UktrError = UktrError(
    code = UktrErrorCodes.BAD_REQUEST_400,
    message = "Invalid message content.",
    logID = Some("C0000AB8190C86300000000200006836")
  )
}

object InvalidJsonError400DynamicErrorMessage {
  def response(errorMessage: String): UktrError = UktrError(
    code = UktrErrorCodes.BAD_REQUEST_400,
    message = "Invalid JSON message content: " + errorMessage,
    logID = Some("C000BADJSON000000000000000000400")
  )
}
