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

import play.api.libs.json.JsObject
import uk.gov.hmrc.pillar2externalteststub.helpers.AccountActivityDataResponses
import uk.gov.hmrc.pillar2externalteststub.models.error.TestDataNotFound
import uk.gov.hmrc.pillar2externalteststub.models.organisation.AccountActivityScenario.*
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId

import java.time.{Clock, LocalDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AccountActivityService @Inject() (clock: Clock) {

  def getAccountActivity(orgWithId: TestOrganisationWithId): Future[JsObject] = {
    val now       = LocalDateTime.now(clock)
    val pillar2Id = orgWithId.pillar2Id

    orgWithId.organisation.testData match {
      case Some(data) =>
        val response = data.accountActivityScenario match {
          case DTT_CHARGE                            => AccountActivityDataResponses.DTTChargeResponse(now)
          case FULLY_PAID_CHARGE                     => AccountActivityDataResponses.FullyPaidChargeResponse(now)
          case FULLY_PAID_CHARGE_WITH_SPLIT_PAYMENTS => AccountActivityDataResponses.FullyPaidChargeWithSplitPaymentsResponse(now)
          case REPAYMENT_INTEREST                    => AccountActivityDataResponses.RepaymentInterestResponse(now)
          case DTT_DETERMINATION                     => AccountActivityDataResponses.DTTDeterminationResponse(now)
        }
        Future.successful(response)

      case None =>
        Future.failed(TestDataNotFound(pillar2Id))
    }
  }
}
