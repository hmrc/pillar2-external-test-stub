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

import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.mvc.Results._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSimpleError._

sealed trait TestScenario {
  def code: String
  def response: Result
  def description: String
}

object TestScenarios {
  sealed trait UKTRScenario extends TestScenario
  sealed trait BTNScenario extends TestScenario

  object UKTR {
    case object InvalidReturn extends UKTRScenario {
      override val code = "INVALID_RETURN_093"
      override val description = "Simulates an invalid return submission"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid return")))
    }

    case object InvalidDTTElection extends UKTRScenario {
      override val code = "INVALID_DTT_ELECTION_094"
      override val description = "Simulates an invalid DTT election"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid DTT election")))
    }

    case object InvalidUTPRElection extends UKTRScenario {
      override val code = "INVALID_UTPR_ELECTION_095"
      override val description = "Simulates an invalid UTPR election"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid UTPR election")))
    }

    case object InvalidTotalLiability extends UKTRScenario {
      override val code = "INVALID_TOTAL_LIABILITY_096"
      override val description = "Simulates an invalid total liability"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid total liability")))
    }

    case object InvalidTotalLiabilityIIR extends UKTRScenario {
      override val code = "INVALID_TOTAL_LIABILITY_IIR_097"
      override val description = "Simulates an invalid total liability IIR"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid total liability IIR")))
    }

    case object InvalidTotalLiabilityDTT extends UKTRScenario {
      override val code = "INVALID_TOTAL_LIABILITY_DTT_098"
      override val description = "Simulates an invalid total liability DTT"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid total liability DTT")))
    }

    case object InvalidTotalLiabilityUTPR extends UKTRScenario {
      override val code = "INVALID_TOTAL_LIABILITY_UTPR_099"
      override val description = "Simulates an invalid total liability UTPR"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid total liability UTPR")))
    }

    case object MissingPLRRef extends UKTRScenario {
      override val code = "MISSING_PLR_REFERENCE_002"
      override val description = "Simulates a missing PLR reference"
      override def response = UnprocessableEntity(Json.toJson(MissingPLRReference))
    }

    case object TaxObligationMet extends UKTRScenario {
      override val code = "TAX_OBLIGATION_FULFILLED_044"
      override val description = "Simulates a fulfilled tax obligation"
      override def response = UnprocessableEntity(Json.toJson(TaxObligationFulfilled))
    }

    case object ServerError extends UKTRScenario {
      override val code = "SERVER_ERROR_500"
      override val description = "Simulates a server error"
      override def response = InternalServerError(Json.toJson(SAPError))
    }

    case object BadRequest extends UKTRScenario {
      override val code = "BAD_REQUEST_400"
      override val description = "Simulates a bad request"
      override def response = BadRequest(Json.toJson(DefaultInvalidJsonError))
    }

    val all: Set[UKTRScenario] = Set(
      InvalidReturn,
      InvalidDTTElection,
      InvalidUTPRElection,
      InvalidTotalLiability,
      InvalidTotalLiabilityIIR,
      InvalidTotalLiabilityDTT,
      InvalidTotalLiabilityUTPR,
      MissingPLRRef,
      TaxObligationMet,
      ServerError,
      BadRequest
    )
  }

  object BTN {
    case object InvalidPeriod extends BTNScenario {
      override val code = "BTN_INVALID_PERIOD"
      override val description = "Simulates an invalid accounting period"
      override def response = UnprocessableEntity(Json.toJson(InvalidJsonError("Invalid accounting period")))
    }

    case object ServerError extends BTNScenario {
      override val code = "BTN_SERVER_ERROR"
      override val description = "Simulates a server error for BTN"
      override def response = InternalServerError(Json.toJson(SAPError))
    }

    val all: Set[BTNScenario] = Set(
      InvalidPeriod,
      ServerError
    )
  }

  def findByCode(code: String): Option[TestScenario] = {
    val allScenarios = UKTR.all ++ BTN.all
    allScenarios.find(_.code == code)
  }

  def values: Set[TestScenario] = UKTR.all ++ BTN.all
} 