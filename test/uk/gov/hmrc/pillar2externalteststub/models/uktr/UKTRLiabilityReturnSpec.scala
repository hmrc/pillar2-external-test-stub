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

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json

class UKTRLiabilityReturnSpec extends AnyFreeSpec with Matchers with UKTRDataFixture with MockitoSugar {

  "UKTRLiabilityReturn validation" - {
    val validLiabilityReturn = Json.fromJson[UKTRLiabilityReturn](validRequestBody).get

    "totalLiabilityRule" - {
      "should pass when total liability equals sum of components" in {
        val result = UKTRLiabilityReturn.totalLiabilityRule.validate(validLiabilityReturn)
        result mustEqual valid(validLiabilityReturn)
      }

      "should fail when total liability does not match sum of components" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiability = BigDecimal(50000.00)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiability))
      }

      "should fail when total liability is negative" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiability = BigDecimal(-500)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiability))
      }

      "should fail when total liability exceeds maximum allowed amount" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiability = BigDecimal("9999999999999.99") + 1
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiability))
      }
    }

    "totalLiabilityDTTRule" - {
      "should pass when DTT total matches sum of DTT amounts" in {
        val result = UKTRLiabilityReturn.totalLiabilityDTTRule.validate(validLiabilityReturn)
        result mustEqual valid(validLiabilityReturn)
      }

      "should fail when DTT total does not match sum of DTT amounts" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiabilityDTT = BigDecimal(1000.00)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityDTTRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityDTT))
      }

      "should fail when DTT total is negative" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiabilityDTT = BigDecimal(-100)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityDTTRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityDTT))
      }
    }

    "totalLiabilityIIRRule" - {
      "should pass when IIR total matches sum of IIR amounts" in {
        val result = UKTRLiabilityReturn.totalLiabilityIIRRule.validate(validLiabilityReturn)
        result mustEqual valid(validLiabilityReturn)
      }

      "should fail when IIR total does not match sum of IIR amounts" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiabilityIIR = BigDecimal(1000.00)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityIIRRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityIIR))
      }

      "should fail when IIR total is negative" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiabilityIIR = BigDecimal(-100)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityIIRRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityIIR))
      }
    }

    "totalLiabilityUTPRRule" - {
      "should pass when UTPR total matches sum of UTPR amounts" in {
        val result = UKTRLiabilityReturn.totalLiabilityUTPRRule.validate(validLiabilityReturn)
        result mustEqual valid(validLiabilityReturn)
      }

      "should fail when UTPR total does not match sum of UTPR amounts" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiabilityUTPR = BigDecimal(1000.00)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityUTPRRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityUTPR))
      }

      "should fail when UTPR total is negative" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiabilityUTPR = BigDecimal(-100)
          )
        )
        val result = UKTRLiabilityReturn.totalLiabilityUTPRRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityUTPR))
      }
    }

    "liabilityEntityRule" - {
      "should pass when liableEntities is non-empty" in {
        val result = UKTRLiabilityReturn.liabilityEntityRule.validate(validLiabilityReturn)
        result mustEqual valid(validLiabilityReturn)
      }

      "should fail when liableEntities is empty" in {
        val invalidReturn = validLiabilityReturn.copy(
          liabilities = validLiabilityReturn.liabilities.copy(
            liableEntities = Seq.empty
          )
        )
        val result = UKTRLiabilityReturn.liabilityEntityRule.validate(invalidReturn)
        result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
      }
    }
  }
}

