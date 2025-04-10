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

package uk.gov.hmrc.pillar2externalteststub.models.subscription

import play.api.libs.json._

trait SubscriptionResponse

object SubscriptionResponse {
  implicit val writes: Writes[SubscriptionResponse] = {
    case success: SubscriptionSuccessResponse => Json.toJson(success)(SubscriptionSuccessResponse.writes)
    case error:   ErrorResponse               => Json.toJson(error)(ErrorResponse.format)
    case _ => throw new IllegalStateException("Unknown SubscriptionResponse type")
  }
}
