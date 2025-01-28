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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper.nowZonedDateTime
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes._

case class UKTRSimpleError(code: String, message: String, logID: Option[String])

object UKTRSimpleError {
  implicit val format: OFormat[UKTRSimpleError] = Json.format[UKTRSimpleError]

  def InvalidJsonError(message: String = "Invalid JSON payload error"): UKTRSimpleError = 
    UKTRSimpleError(BAD_REQUEST_400, message, Some(nowZonedDateTime))

  val DefaultInvalidJsonError: UKTRSimpleError = InvalidJsonError()

  val SAPError: UKTRSimpleError = UKTRSimpleError(INTERNAL_SERVER_ERROR_500, "SAP system failure: ...", Some(nowZonedDateTime))
} 