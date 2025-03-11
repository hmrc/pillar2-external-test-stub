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
  override def getMessage: String = message
}

object ETMPError {
  case object Pillar2IdMissing extends ETMPError {
    override val code:    String = "002"
    override val message: String = s"Pillar2 ID Missing or Invalid"
  }

  case object RequestCouldNotBeProcessed extends ETMPError {
    override val code:    String = "003"
    override val message: String = s"Request could not be processed"
  }

  case object DuplicateSubmissionError extends ETMPError {
    override val code:    String = "004"
    override val message: String = s"Duplicate submission acknowledgment reference"
  }

  case object NoActiveSubscription extends ETMPError {
    override val code:    String = "007"
    override val message: String = s"Business partner does not have an Active subscription"
  }

  case object TaxObligationAlreadyFulfilled extends ETMPError {
    override val code:    String = "044"
    override val message: String = s"Tax obligation already fulfilled"
  }

  case object InvalidReturn extends ETMPError {
    override val code:    String = "093"
    override val message: String = "Invalid return"
  }

  case object InvalidDTTElection extends ETMPError {
    override val code:    String = "094"
    override val message: String = "Invalid DTT election"
  }

  case object InvalidUTPRElection extends ETMPError {
    override val code:    String = "095"
    override val message: String = "Invalid UTPR election"
  }

  case object InvalidTotalLiability extends ETMPError {
    override val code:    String = "096"
    override val message: String = "Invalid total liability"
  }

  case object InvalidTotalLiabilityIIR extends ETMPError {
    override val code:    String = "097"
    override val message: String = "Invalid total liability IIR"
  }

  case object InvalidTotalLiabilityDTT extends ETMPError {
    override val code:    String = "098"
    override val message: String = "Invalid total liability DTT"
  }

  case object InvalidTotalLiabilityUTPR extends ETMPError {
    override val code:    String = "099"
    override val message: String = "Invalid total liability UTPR"
  }

  case object ETMPBadRequest extends ETMPError {
    override val code:    String = "400"
    override val message: String = "Bad request"
  }

  case object ETMPInternalServerError extends ETMPError {
    override val code:    String = "500"
    override val message: String = "Internal server error"
  }
}
