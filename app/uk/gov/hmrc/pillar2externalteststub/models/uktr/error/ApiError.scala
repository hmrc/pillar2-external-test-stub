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

package uk.gov.hmrc.pillar2externalteststub.models.uktr.error

import play.api.libs.json.Json
import play.api.libs.json.Writes

sealed trait ApiError
case class DetailedError(errors: UKTRErrorDetail) extends ApiError
case class SimpleError(error: UKTRError) extends ApiError

object ApiError {
  implicit val writes: Writes[ApiError] = Writes {
    case d: DetailedError => Json.obj("errors" -> d.errors)
    case s: SimpleError   => Json.obj("error" -> s.error)
  }
}
