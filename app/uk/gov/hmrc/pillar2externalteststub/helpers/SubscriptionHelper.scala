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

package uk.gov.hmrc.pillar2externalteststub.helpers

import play.api.mvc.Results.*
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse.{successfulDomesticOnlyResponse, successfulNonDomesticResponse}
import uk.gov.hmrc.pillar2externalteststub.models.subscription.*

object SubscriptionHelper {
  def isDomesticOnly(plrReference: String): Boolean =
    retrieveSubscription(plrReference)._2 match {
      case success: SubscriptionSuccessResponse => success.upeDetails.domesticOnly
      case _ => throw new IllegalStateException(s"Unable to fetch subscription for pillar2 ID: $plrReference")
    }

  def retrieveSubscription(plrReference: String): (Status, SubscriptionResponse) =
    plrReference match {
      case "XEPLR0123456500" => (InternalServerError, ServerError500.response)
      case "XEPLR0123456503" => (ServiceUnavailable, ServiceUnavailable503.response)
      case "XEPLR5555555554" => (NotFound, NotFoundSubscription.response)
      case "XEPLR1234567890" => (Ok, successfulNonDomesticResponse)
      case _                 => (Ok, successfulDomesticOnlyResponse)
    }
}
