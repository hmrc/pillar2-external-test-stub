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

package uk.gov.hmrc.pillar2externalteststub.models.uktr

import play.api.libs.json.{Json, OFormat, Writes}

sealed trait UKTRResponse

case class LiabilitySuccessResponse(success: LiabilityReturnSuccess) extends UKTRResponse

object LiabilitySuccessResponse {
  given format: OFormat[LiabilitySuccessResponse] = Json.format[LiabilitySuccessResponse]
}
case class NilSuccessResponse(success: NilReturnSuccess) extends UKTRResponse

object NilSuccessResponse {
  given format: OFormat[NilSuccessResponse] = Json.format[NilSuccessResponse]
}

object UKTRResponse {
  given writes: Writes[UKTRResponse] = Writes {
    case l: LiabilitySuccessResponse => Json.obj("success" -> l.success)
    case n: NilSuccessResponse       => Json.obj("success" -> n.success)
  }
}
