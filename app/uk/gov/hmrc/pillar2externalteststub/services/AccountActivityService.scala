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

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AccountActivityService @Inject() (responses: AccountActivityDataResponses) {

  def getAccountActivity(orgWithId: TestOrganisationWithId): Future[JsObject] = {
    val pillar2Id = orgWithId.pillar2Id

    orgWithId.organisation.testData match {
      case Some(data) =>
        val response = data.accountActivityScenario match {
          case DTT_CHARGE                            => responses.DTTChargeResponse
          case FULLY_PAID_CHARGE                     => responses.FullyPaidChargeResponse
          case FULLY_PAID_CHARGE_WITH_SPLIT_PAYMENTS => responses.FullyPaidChargeWithSplitPaymentsResponse
          case REPAYMENT_INTEREST                    => responses.RepaymentInterestResponse
          case DTT_DETERMINATION                     => responses.DTTDeterminationResponse
        }
        Future.successful(response)

      case None =>
        Future.failed(TestDataNotFound(pillar2Id))
    }
  }
}
