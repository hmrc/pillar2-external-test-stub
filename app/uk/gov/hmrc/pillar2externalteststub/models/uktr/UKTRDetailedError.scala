/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json._
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.nowZonedDateTime

sealed trait UKTRDetailedError {
  def code:    String
  def message: String
}

object UKTRDetailedError {
  case object MissingPLRReference extends UKTRDetailedError {
    val code:    String = UKTRErrorCodes.PILLAR_2_ID_MISSING_OR_INVALID_002
    val message: String = "PLR Reference is missing or invalid"
  }

  case class SubscriptionNotFound(plrReference: String) extends UKTRDetailedError {
    val code:    String = UKTRErrorCodes.BUSINESS_PARTNER_DOES_NOT_HAVE_AN_ACTIVE_SUBSCRIPTION_007
    val message: String = s"No active subscription found for PLR Reference: $plrReference"
  }

  case object DuplicateSubmissionError extends UKTRDetailedError {
    val code:    String = UKTRErrorCodes.DUPLICATE_SUBMISSION_044
    val message: String = "A submission already exists for this accounting period"
  }

  case object RequestCouldNotBeProcessed extends UKTRDetailedError {
    override val code:    String = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
    override val message: String = "Request could not be processed"
  }

  case class InvalidAccountingPeriod(submittedStart: String, submittedEnd: String, registeredStart: String, registeredEnd: String)
      extends UKTRDetailedError {
    override val code: String = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
    override val message: String =
      s"Accounting period ($submittedStart to $submittedEnd) does not match the registered period ($registeredStart to $registeredEnd)"
  }

  case class InvalidNilReturnDomesticMTT(isDomestic: Boolean) extends UKTRDetailedError {
    override val code: String = UKTRErrorCodes.INVALID_RETURN_093
    override val message: String =
      if (isDomestic) "obligationMTT cannot be true for a domestic-only group"
      else "electionUKGAAP can be true only for a domestic-only group"
  }

  implicit val writes: Writes[UKTRDetailedError] = new Writes[UKTRDetailedError] {
    def writes(error: UKTRDetailedError): JsValue = Json.obj(
      "processingDate" -> nowZonedDateTime.toString,
      "code"           -> error.code,
      "text"           -> error.message
    )
  }

  implicit val reads: Reads[UKTRDetailedError] = Reads { _ =>
    JsError("Reading UKTRDetailedError is not supported")
  }

  implicit val format: Format[UKTRDetailedError] = Format(reads, writes)
}
