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

import play.api.mvc.Results._
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse.{successfulDomesticOnlyResponse, successfulNonDomesticResponse}
import uk.gov.hmrc.pillar2externalteststub.models.subscription._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.response.NilReturnSubscriptionSuccess

object SubscriptionHelper {
  def retrieveSubscription(plrReference: String): (Status, SubscriptionResponse) =
    plrReference match {
      case "XEPLR0123456404" => (NotFound, NotFoundSubscription.response)
      case "XEPLR0123456500" => (InternalServerError, ServerError500.response)
      case "XEPLR0123456503" => (ServiceUnavailable, ServiceUnavailable503.response)
      case "XEPLR5555555555" => (Ok, successfulDomesticOnlyResponse)
      case "XEPLR1234567890" => (Ok, successfulNonDomesticResponse)
      case "XEPLR0987654321" => (Ok, NilReturnSubscriptionSuccess.successfulResponse)
      case _                 => (NotFound, NotFoundSubscription.response)
    }
}
