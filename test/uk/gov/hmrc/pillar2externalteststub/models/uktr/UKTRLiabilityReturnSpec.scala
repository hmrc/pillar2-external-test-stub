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

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito._
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{ValidationResult, invalid, valid}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class UKTRLiabilityReturnSpec extends AnyFreeSpec with Matchers with UKTRDataFixture with MockitoSugar with TestOrgDataFixture {

  "UKTRLiabilityReturn validation" - {
    when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
    val validLiabilityReturn = Json.fromJson[UKTRLiabilityReturn](validRequestBody).get

    "should pass validation for a valid liability return" in {
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(validLiabilityReturn)), 5.seconds)
      result mustEqual valid(validLiabilityReturn)
    }

    "should fail validation when total liability does not match sum of components" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(50000.00)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiability))
    }

    "should fail validation when total liability is negative" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(-500)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiability))
    }

    "should fail validation when total liability exceeds maximum allowed amount" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal("9999999999999.99") + 1
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiability))
    }

    "should fail validation when DTT total does not match sum of DTT amounts" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity => entity.copy(amountOwedDTT = BigDecimal(1000.00)))
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityDTT))
    }

    "should fail validation when DTT total is negative" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(100),
          totalLiabilityDTT = BigDecimal(-100)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityDTT))
    }

    "should fail validation when IIR total does not match sum of IIR amounts" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(250),
          totalLiabilityIIR = BigDecimal(50)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityIIR))
    }

    "should fail validation when IIR total is negative" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(100),
          totalLiabilityIIR = BigDecimal(-100)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityIIR))
    }

    "should fail validation when UTPR total does not match sum of UTPR amounts" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(250),
          totalLiabilityUTPR = BigDecimal(50)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityUTPR))
    }

    "should fail validation when UTPR total is negative" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(100),
          totalLiabilityUTPR = BigDecimal(-100)
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityUTPR))
    }

    "should fail validation when liableEntities is empty" in {
      val invalidReturn = validLiabilityReturn.copy(
        liabilities = validLiabilityReturn.liabilities.copy(
          electionDTTSingleMember = false,
          electionUTPRSingleMember = false,
          numberSubGroupDTT = 0,
          numberSubGroupUTPR = 0,
          liableEntities = Seq.empty
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when obligationMTT is true for domestic organisation" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))
      val invalidReturn = validLiabilityReturn.copy(
        obligationMTT = true
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when electionUKGAAP is true for non-domestic organisation" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
      val invalidReturn = validLiabilityReturn.copy(
        electionUKGAAP = true
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when accounting period doesn't match organisation's" in {
      val invalidReturn = validLiabilityReturn.copy(
        accountingPeriodFrom = validLiabilityReturn.accountingPeriodFrom.plusDays(1),
        accountingPeriodTo = validLiabilityReturn.accountingPeriodTo.plusDays(1)
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when accounting period accountingPeriodTo is after the accountingPeriodFrom" in {
      val invalidReturn = validLiabilityReturn.copy(accountingPeriodTo = validLiabilityReturn.accountingPeriodFrom.minusYears(1))

      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail when a domestic-only organisation with obligationMTT = false has a positive total" - {
      "total IIR liability is not nil" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))

        val invalidReturn = validLiabilityReturn.copy(
          obligationMTT = false,
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiability = BigDecimal(300),
            totalLiabilityIIR = BigDecimal(100)
          )
        )
        val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityIIR))
      }

      "total UTPR liability is not nil" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))

        val invalidReturn = validLiabilityReturn.copy(
          obligationMTT = false,
          liabilities = validLiabilityReturn.liabilities.copy(
            totalLiability = BigDecimal(200),
            totalLiabilityIIR = BigDecimal(0),
            totalLiabilityUTPR = BigDecimal(100),
            liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity => entity.copy(amountOwedIIR = BigDecimal(0)))
          )
        )
        val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
        result mustEqual invalid(UKTRSubmissionError(InvalidTotalLiabilityUTPR))
      }
    }

    "should fail validation when obligationMTT is false and IIR amount is greater than zero" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
      val invalidReturn = validLiabilityReturn.copy(
        obligationMTT = false,
        liabilities = validLiabilityReturn.liabilities.copy(
          electionDTTSingleMember = false,
          electionUTPRSingleMember = false,
          totalLiability = BigDecimal(200),
          totalLiabilityIIR = BigDecimal(100),
          totalLiabilityUTPR = BigDecimal(0),
          totalLiabilityDTT = BigDecimal(100),
          liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity =>
            entity.copy(amountOwedIIR = BigDecimal(100), amountOwedUTPR = BigDecimal(0), amountOwedDTT = BigDecimal(100))
          )
        )
      )

      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when obligationMTT is false and individual entity IIR amount is greater than zero even if total is zero" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
      val invalidReturn = validLiabilityReturn.copy(
        obligationMTT = false,
        liabilities = validLiabilityReturn.liabilities.copy(
          electionDTTSingleMember = false,
          electionUTPRSingleMember = false,
          totalLiability = BigDecimal(100),
          totalLiabilityDTT = BigDecimal(0),
          totalLiabilityIIR = BigDecimal(100),
          totalLiabilityUTPR = BigDecimal(0),
          liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity =>
            entity.copy(amountOwedDTT = BigDecimal(0), amountOwedIIR = BigDecimal(100), amountOwedUTPR = BigDecimal(0))
          )
        )
      )

      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when obligationMTT is false and UTPR amount is greater than zero" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

      val invalidReturn = validLiabilityReturn.copy(
        obligationMTT = false,
        liabilities = validLiabilityReturn.liabilities.copy(
          electionUTPRSingleMember = false,
          numberSubGroupUTPR = 0,
          totalLiability = BigDecimal(200),
          totalLiabilityIIR = BigDecimal(0),
          totalLiabilityUTPR = BigDecimal(100),
          liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity =>
            entity.copy(amountOwedIIR = BigDecimal(0), amountOwedUTPR = BigDecimal(100))
          )
        )
      )

      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when obligationMTT is false and individual entity UTPR amount is greater than zero even if total is zero" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

      val invalidReturn = validLiabilityReturn.copy(
        obligationMTT = false,
        liabilities = validLiabilityReturn.liabilities.copy(
          electionDTTSingleMember = false,
          numberSubGroupDTT = 0,
          electionUTPRSingleMember = false,
          numberSubGroupUTPR = 0,
          totalLiability = BigDecimal(100),
          totalLiabilityDTT = BigDecimal(0),
          totalLiabilityIIR = BigDecimal(0),
          totalLiabilityUTPR = BigDecimal(100),
          liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity =>
            entity.copy(amountOwedDTT = BigDecimal(0), amountOwedIIR = BigDecimal(0), amountOwedUTPR = BigDecimal(100))
          )
        )
      )

      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should pass validation when obligationMTT is false and both IIR and UTPR amounts are zero" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

      val validReturn = validLiabilityReturn.copy(
        obligationMTT = false,
        liabilities = validLiabilityReturn.liabilities.copy(
          electionUTPRSingleMember = false,
          numberSubGroupUTPR = 0,
          totalLiability = BigDecimal(100),
          totalLiabilityIIR = BigDecimal(0),
          totalLiabilityUTPR = BigDecimal(0),
          liableEntities =
            validLiabilityReturn.liabilities.liableEntities.map(entity => entity.copy(amountOwedIIR = BigDecimal(0), amountOwedUTPR = BigDecimal(0)))
        )
      )

      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(validReturn)), 5.seconds)
      result mustEqual valid(validReturn)
    }

    "should pass validation when obligationMTT is true regardless of IIR and UTPR amounts" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

      val validReturn = validLiabilityReturn.copy(
        obligationMTT = true,
        liabilities = validLiabilityReturn.liabilities.copy(
          totalLiability = BigDecimal(300),
          totalLiabilityIIR = BigDecimal(100),
          totalLiabilityUTPR = BigDecimal(100),
          liableEntities = validLiabilityReturn.liabilities.liableEntities.map(entity =>
            entity.copy(amountOwedIIR = BigDecimal(100), amountOwedUTPR = BigDecimal(100))
          )
        )
      )
      val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(validReturn)), 5.seconds)
      result mustEqual valid(validReturn)
    }

    "should fail validation when election DTT data is invalid" - {
      def result(implicit invalidReturn: UKTRLiabilityReturn): ValidationResult[UKTRLiabilityReturn] =
        Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)

      "electionDTTSingleMember = true yet numberSubGroupDTT = 0" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        implicit val invalidReturn: UKTRLiabilityReturn =
          validLiabilityReturn.copy(liabilities = validLiabilityReturn.liabilities.copy(numberSubGroupDTT = 0))

        result mustEqual invalid(UKTRSubmissionError(InvalidDTTElection))
      }

      "electionDTTSingleMember = true and the number of sub-groups does not match the liabile entities with positive amountOwedDTT" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        implicit val invalidReturn: UKTRLiabilityReturn =
          validLiabilityReturn.copy(liabilities = validLiabilityReturn.liabilities.copy(numberSubGroupDTT = 2))

        result mustEqual invalid(UKTRSubmissionError(InvalidDTTElection))
      }

      "invalid number of sub-groups" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        implicit val invalidReturn: UKTRLiabilityReturn =
          validLiabilityReturn.copy(liabilities = validLiabilityReturn.liabilities.copy(numberSubGroupDTT = -1))

        result mustEqual invalid(UKTRSubmissionError(InvalidDTTElection))
      }
    }

    "should fail validation when election UTPR data is invalid" - {
      "electionUTPRSingleMember = true yet numberSubGroupUTPR = 0" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        val invalidReturn = validLiabilityReturn.copy(liabilities = validLiabilityReturn.liabilities.copy(numberSubGroupUTPR = 0))

        val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
        result mustEqual invalid(UKTRSubmissionError(InvalidUTPRElection))
      }

      "electionUTPRSingleMember = true and the number of sub-groups does not match liabile entities with positive amountOwedUTPR" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        val invalidReturn =
          validLiabilityReturn.copy(liabilities = validLiabilityReturn.liabilities.copy(numberSubGroupUTPR = 2))

        val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
        result mustEqual invalid(UKTRSubmissionError(InvalidUTPRElection))
      }

      "invalid number of sub-groups" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        val invalidReturn = validLiabilityReturn.copy(liabilities = validLiabilityReturn.liabilities.copy(numberSubGroupUTPR = -1))

        val result = Await.result(UKTRLiabilityReturn.uktrSubmissionValidator("validPlrId").map(_.validate(invalidReturn)), 5.seconds)
        result mustEqual invalid(UKTRSubmissionError(InvalidUTPRElection))
      }
    }
  }
}
