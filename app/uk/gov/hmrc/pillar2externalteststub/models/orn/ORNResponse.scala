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

package uk.gov.hmrc.pillar2externalteststub.models.orn

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.generateFormBundleNumber

import java.time.temporal.ChronoUnit
import java.time.{ZoneOffset, ZonedDateTime}

trait ORNResponse

object ORNResponse {
  def now: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS)
}

case class ORNSuccessResponse(success: ORNSuccess) extends ORNResponse

object ORNSuccessResponse {
  implicit val format: OFormat[ORNSuccessResponse] = Json.format[ORNSuccessResponse]

  def ORN_SUCCESS_201: ORNSuccessResponse = ORNSuccessResponse(
    ORNSuccess(
      processingDate = ORNResponse.now,
      formBundleNumber = generateFormBundleNumber()
    )
  )

  def ORN_SUCCESS_200: ORNSuccessResponse = ORNSuccessResponse(
    ORNSuccess(
      processingDate = ORNResponse.now,
      formBundleNumber = generateFormBundleNumber()
    )
  )
}

case class ORNSuccess(processingDate: ZonedDateTime, formBundleNumber: String)

object ORNSuccess {
  implicit val format: OFormat[ORNSuccess] = Json.format[ORNSuccess]
}
