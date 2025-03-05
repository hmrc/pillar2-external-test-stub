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

import play.api.libs.json.OFormat
import play.api.libs.json.Json

case class ETMPErrorResponse(error: ETMPSimpleError)

object ETMPErrorResponse {
  implicit val format: OFormat[ETMPErrorResponse] = Json.format[ETMPErrorResponse]
}

case class ETMPSimpleError(code: String, message: String, logID: Option[String] = None)

object ETMPSimpleError {
  implicit val format: OFormat[ETMPSimpleError] = Json.format[ETMPSimpleError]
}

case class ETMPFailureResponse(errors: ETMPDetailedError)

object ETMPFailureResponse {
  implicit val format: OFormat[ETMPFailureResponse] = Json.format[ETMPFailureResponse]
}

case class ETMPDetailedError(processingDate: String, code: String, text: String)

object ETMPDetailedError {
  implicit val format: OFormat[ETMPDetailedError] = Json.format[ETMPDetailedError]
}
