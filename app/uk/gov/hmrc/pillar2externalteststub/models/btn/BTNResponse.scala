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

package uk.gov.hmrc.pillar2externalteststub.models.btn

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNResponse.now

import java.time.temporal.ChronoUnit
import java.time.{ZoneOffset, ZonedDateTime}

trait BTNResponse

object BTNResponse {
  def now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
}

case class BTNSuccessResponse(success: BTNSuccess) extends BTNResponse

object BTNSuccessResponse {
  implicit val format: OFormat[BTNSuccessResponse] = Json.format[BTNSuccessResponse]

  def BTN_SUCCESS_201: BTNSuccessResponse = BTNSuccessResponse(
    BTNSuccess(
      processingDate = now
    )
  )
}

case class BTNSuccess(processingDate: ZonedDateTime)

object BTNSuccess {
  implicit val format: OFormat[BTNSuccess] = Json.format[BTNSuccess]
}
