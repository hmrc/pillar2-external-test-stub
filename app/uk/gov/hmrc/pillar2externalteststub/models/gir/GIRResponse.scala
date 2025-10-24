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

package uk.gov.hmrc.pillar2externalteststub.models.gir

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRResponse.now

import java.time.temporal.ChronoUnit
import java.time.{ZoneOffset, ZonedDateTime}

trait GIRResponse

object GIRResponse {
  def now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
}

case class GIRSuccessResponse(success: GIRSuccess) extends GIRResponse

object GIRSuccessResponse {
  given format: OFormat[GIRSuccessResponse] = Json.format[GIRSuccessResponse]

  def GIR_SUCCESS_201: GIRSuccessResponse = GIRSuccessResponse(
    GIRSuccess(
      processingDate = now
    )
  )
}

case class GIRSuccess(processingDate: ZonedDateTime)

object GIRSuccess {
  given format: OFormat[GIRSuccess] = Json.format[GIRSuccess]
}
