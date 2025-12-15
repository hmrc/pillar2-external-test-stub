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
import uk.gov.hmrc.pillar2externalteststub.helpers.AccountActivityDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.TestDataNotFound
import uk.gov.hmrc.pillar2externalteststub.models.organisation.AccountActivityScenario.*
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId

import javax.inject.{Inject, Singleton}
import scala.concurrent.Future

@Singleton
class AccountActivityService @Inject() {

  def getAccountActivity(orgWithId: TestOrganisationWithId): Future[JsObject] = Future.successful {
    val pillar2Id = orgWithId.pillar2Id
    val scenario = orgWithId.organisation.testData
      .map(_.accountActivityScenario)

    scenario.fold[JsObject] {
      throw TestDataNotFound(pillar2Id)
    } {
      case SOLE_CHARGE                           => AccountActivityDataFixture.SoleChargeResponse
      case FULLY_PAID_CHARGE                     => AccountActivityDataFixture.FullyPaidChargeResponse
      case FULLY_PAID_CHARGE_WITH_SPLIT_PAYMENTS => AccountActivityDataFixture.FullyPaidChargeWithSplitPaymentsResponse
    }
  }
}
