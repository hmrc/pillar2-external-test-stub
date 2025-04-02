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

package uk.gov.hmrc.pillar2externalteststub.models.common

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import play.api.libs.json._

class MonetaryReadsSpec extends AnyFreeSpec with Matchers {

  val reads = MonetaryReads.monetaryValueReads
  val validationError: JsonValidationError = JsonValidationError(
    "Invalid monetary amount: must be between 0 and 9999999999999.99 with up to 2 decimal places."
  )

  "MonetaryReads" - {

    "must successfully read valid monetary values" - {
      "when value is 0" in {
        reads.reads(JsNumber(BigDecimal(0))) mustBe JsSuccess(BigDecimal(0))
      }
      "when value is 0.00" in {
        reads.reads(JsNumber(BigDecimal(0.00))) mustBe JsSuccess(BigDecimal(0.00))
      }
      "when value is the upper bound (9999999999999.99)" in {
        val upperBound = BigDecimal("9999999999999.99")
        reads.reads(JsNumber(upperBound)) mustBe JsSuccess(upperBound)
      }
      "when value is a typical amount (123.45)" in {
        reads.reads(JsNumber(BigDecimal(123.45))) mustBe JsSuccess(BigDecimal(123.45))
      }
      "when value has 1 decimal place" in {
        reads.reads(JsNumber(BigDecimal(123.4))) mustBe JsSuccess(BigDecimal(123.4))
      }
    }

    "must fail to read invalid monetary values" - {
      "when value is negative (-0.01)" in {
        reads.reads(JsNumber(BigDecimal(-0.01))) mustBe JsError(validationError)
      }
      "when value is slightly above the upper bound (10000000000000.00)" in {
        val justOver = BigDecimal("10000000000000.00")
        reads.reads(JsNumber(justOver)) mustBe JsError(validationError)
      }
      "when value has more than 2 decimal places (0.123)" in {
        reads.reads(JsNumber(BigDecimal(0.123))) mustBe JsError(validationError)
      }
      "when value is the upper bound but has too many decimal places (9999999999999.991)" in {
        val upperBoundWrongScale = BigDecimal("9999999999999.991")
        reads.reads(JsNumber(upperBoundWrongScale)) mustBe JsError(validationError)
      }
    }
  }
}
