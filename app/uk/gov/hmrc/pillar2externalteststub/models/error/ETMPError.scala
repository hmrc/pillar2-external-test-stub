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

package uk.gov.hmrc.pillar2externalteststub.models.error

sealed trait ETMPError extends Exception {
  def code:    String
  def message: String
  def logID:               Option[String] = None
  override def getMessage: String         = message
}

object ETMPError {
  case object RequestCouldNotBeProcessed extends ETMPError {
    override val code:    String = "003"
    override val message: String = "Request could not be processed"
  }

  case object DuplicateSubmission extends ETMPError {
    override val code:    String = "004"
    override val message: String = "Duplicate submission"
  }

  case object NoActiveSubscription extends ETMPError {
    override val code:    String = "063"
    override val message: String = "Business Partner does not have an Active Subscription for this Regime"
  }

  case object NoDataFound extends ETMPError {
    override val code:    String = "014"
    override val message: String = "No data found"
  }

  case object TaxObligationAlreadyFulfilled extends ETMPError {
    override val code:    String = "044"
    override val message: String = "Tax obligation already fulfilled"
  }

  case object IdMissingOrInvalid extends ETMPError {
    override val code:    String = "089"
    override val message: String = "ID number missing or invalid"
  }

  case object InvalidReturn extends ETMPError {
    override val code:    String = "093"
    override val message: String = "Invalid Return"
  }

  case object InvalidDTTElection extends ETMPError {
    override val code:    String = "094"
    override val message: String = "Invalid DTT Election"
  }

  case object InvalidUTPRElection extends ETMPError {
    override val code:    String = "095"
    override val message: String = "Invalid UTPR Election"
  }

  case object InvalidTotalLiability extends ETMPError {
    override val code:    String = "096"
    override val message: String = "Invalid Total Liability"
  }

  case object InvalidTotalLiabilityIIR extends ETMPError {
    override val code:    String = "097"
    override val message: String = "Invalid Total Liability IIR"
  }

  case object InvalidTotalLiabilityDTT extends ETMPError {
    override val code:    String = "098"
    override val message: String = "Invalid Total Liability DTT"
  }

  case object InvalidTotalLiabilityUTPR extends ETMPError {
    override val code:    String = "099"
    override val message: String = "Invalid Total Liability UTPR"
  }

  case object ETMPBadRequest extends ETMPError {
    override val code:    String         = "400"
    override val message: String         = "Bad request"
    override val logID:   Option[String] = Some("C0000000000000000000000000000400")
  }

  case object ETMPInternalServerError extends ETMPError {
    override val code:    String         = "500"
    override val message: String         = "Internal server error"
    override val logID:   Option[String] = Some("C0000000000000000000000000000500")
  }
}
