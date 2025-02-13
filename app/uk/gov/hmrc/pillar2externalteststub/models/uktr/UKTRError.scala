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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper.nowZonedDateTime
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes._

case class UKTRSimpleError(code: String, message: String, logID: Option[String])

object UKTRSimpleError {
  implicit val format: OFormat[UKTRSimpleError] = Json.format[UKTRSimpleError]

  def InvalidJsonError(errorMessage: String = "Invalid JSON"): SimpleErrorResponse =
    SimpleErrorResponse(
      UKTRSimpleError(
        code = BAD_REQUEST_400,
        message = errorMessage,
        logID = Some("C0000000000000000000000000000400")
      )
    )

  def SAPError: SimpleErrorResponse =
    SimpleErrorResponse(
      UKTRSimpleError(
        code = INTERNAL_SERVER_ERROR_500,
        message = "Internal server error",
        logID = Some("C0000000000000000000000000000500")
      )
    )
}

case class UKTRDetailedError(processingDate: String, code: String, text: String)

object UKTRDetailedError {
  implicit val format: OFormat[UKTRDetailedError] = Json.format[UKTRDetailedError]

  def TaxObligationFulfilled: DetailedErrorResponse = DetailedErrorResponse(
    UKTRDetailedError(
      processingDate = nowZonedDateTime,
      code = TAX_OBLIGATION_ALREADY_FULFILLED_044,
      text = "Tax Obligation Already Fulfilled"
    )
  )

  def SubscriptionNotFound(plrReference: String): DetailedErrorResponse =
    DetailedErrorResponse(
      UKTRDetailedError(
        processingDate = nowZonedDateTime,
        code = BUSINESS_PARTNER_DOES_NOT_HAVE_AN_ACTIVE_SUBSCRIPTION_007,
        text = s"Unable to fetch subscription for pillar2 ID: $plrReference"
      )
    )

  def MissingPLRReference: DetailedErrorResponse =
    DetailedErrorResponse(
      UKTRDetailedError(
        processingDate = nowZonedDateTime,
        code = PILLAR_2_ID_MISSING_OR_INVALID_002,
        text = "Pillar 2 ID missing or invalid"
      )
    )

  def RequestCouldNotBeProcessed: DetailedErrorResponse =
    DetailedErrorResponse(
      UKTRDetailedError(
        processingDate = nowZonedDateTime,
        code = REQUEST_COULD_NOT_BE_PROCESSED_003,
        text = "Request could not be processed"
      )
    )
}

object UKTRErrorCodes {
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
  val INTERNAL_SERVER_ERROR_500                                 = "500"
}
