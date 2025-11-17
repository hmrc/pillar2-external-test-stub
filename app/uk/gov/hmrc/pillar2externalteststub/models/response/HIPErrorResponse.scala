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

package uk.gov.hmrc.pillar2externalteststub.models.response

import enumeratum._
import play.api.libs.json._

case class HIPErrorResponse(origin: Origin, response: HIPFailure)

object HIPErrorResponse {

  given format: OFormat[HIPErrorResponse] = Json.format
}

case class HIPFailure(failures: List[HIPError])

object HIPFailure {
  given format: OFormat[HIPFailure] = Json.format
}

case class HIPError(reason: String, `type`: String)

object HIPError {
  given format: OFormat[HIPError] = Json.format
}

sealed trait Origin extends EnumEntry

object Origin extends Enum[Origin] with PlayJsonEnum[Origin] {
  val values = findValues

  case object HIP extends Origin
  case object HoD extends Origin
}
