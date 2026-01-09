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

package uk.gov.hmrc.pillar2externalteststub.services

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.prop.Tables.Table
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.JsObject
import uk.gov.hmrc.pillar2externalteststub.helpers.AccountActivityDataResponses
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.TestDataNotFound
import uk.gov.hmrc.pillar2externalteststub.models.organisation.*

import java.time.*

class AccountActivityServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with TestOrgDataFixture
    with TableDrivenPropertyChecks
    with ScalaFutures {

  private val fixedInstant = Instant.parse("2025-12-18T10:00:00Z")
  private val stubClock    = Clock.fixed(fixedInstant, ZoneOffset.UTC)
  private val responses    = AccountActivityDataResponses(stubClock)

  private val service = new AccountActivityService(responses)

  private val testAccountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31),
    None
  )

  "AccountActivityService" - {

    "when retrieving account activity" - {
      val scenarioTable = Table(
        "Scenario"                                                       -> "Expected Response",
        AccountActivityScenario.DTT_CHARGE                               -> responses.DTTChargeResponse,
        AccountActivityScenario.FULLY_PAID_CHARGE                        -> responses.FullyPaidChargeResponse,
        AccountActivityScenario.FULLY_PAID_CHARGE_WITH_SPLIT_PAYMENTS    -> responses.FullyPaidChargeWithSplitPaymentsResponse,
        AccountActivityScenario.REPAYMENT_INTEREST                       -> responses.RepaymentInterestResponse,
        AccountActivityScenario.DTT_DETERMINATION                        -> responses.DTTDeterminationResponse,
        AccountActivityScenario.DTT_IIR_UTPR                             -> responses.DttIirUtprResponse,
        AccountActivityScenario.ACCRUED_INTEREST                         -> responses.AccruedInterestResponse,
        AccountActivityScenario.DTT_IIR_UTPR_INTEREST                    -> responses.DttIirUtprInterestResponse,
        AccountActivityScenario.DTT_IIR_UTPR_DETERMINATION               -> responses.DttIirUtprDeterminationResponse,
        AccountActivityScenario.DTT_IIR_UTPR_DISCOVERY                   -> responses.DttIirUtprDiscoveryResponse,
        AccountActivityScenario.DTT_IIR_UTPR_OVERPAID_CLAIM              -> responses.DttIirUtprOverpaidClaimResponse,
        AccountActivityScenario.UKTR_DTT_UKTR_MTT_LATE_FILING_PENALTY    -> responses.UktrDttMttLateFilingPenaltyResponse,
        AccountActivityScenario.ORN_GIR_DTT_UKTR_MTT_LATE_FILING_PENALTY -> responses.OrnGirDttUktrMttLateFilingPenaltyResponse,
        AccountActivityScenario.POTENTIAL_LOST_REVENUE_PENALTY           -> responses.PotentialLostRevenuePenaltyResponse,
        AccountActivityScenario.SCHEDULE_36_PENALTY                      -> responses.Schedule36PenaltyResponse,
        AccountActivityScenario.RECORD_KEEPING_PENALTY                   -> responses.RecordKeepingPenaltyResponse,
        AccountActivityScenario.REPAYMENT_CREDIT                         -> responses.RepaymentCreditResponse,
        AccountActivityScenario.INTEREST_REPAYMENT_CREDIT                -> responses.InterestRepaymentCreditResponse
      )

      "should return the correct response for all defined scenarios" in {
        forAll(scenarioTable) { (scenario, expectedResponse) =>

          val org = TestOrganisation(
            orgDetails = orgDetails,
            accountingPeriod = testAccountingPeriod,
            testData = Some(TestData(scenario)),
            accountStatus = AccountStatus(inactive = false)
          ).withPillar2Id(validPlrId)

          val result = service.getAccountActivity(org).futureValue

          result shouldBe expectedResponse
        }
      }
    }

    "validation failures" - {

      "should fail with TestDataNotFound when testData is missing" in {
        val org = TestOrganisation(
          orgDetails = orgDetails,
          accountingPeriod = testAccountingPeriod,
          testData = None,
          accountStatus = AccountStatus(inactive = false)
        ).withPillar2Id(validPlrId)

        whenReady(service.getAccountActivity(org).failed) { ex =>
          ex shouldBe a[TestDataNotFound]
          val error = ex.asInstanceOf[TestDataNotFound]
          error.code    shouldBe "TEST_DATA_NOT_FOUND"
          error.message shouldBe s"Test Data can not be found for pillar2Id: $validPlrId"
        }
      }
    }
  }
}
