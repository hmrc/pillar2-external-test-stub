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

package uk.gov.hmrc.pillar2externalteststub.models.uktr.response

import play.api.libs.json.{Json, OWrites, Writes}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.UktrBusinessValidationErrorDetail
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error._

sealed trait ApiResponse
case class SuccessResponse(success: SubmitUKTRSuccessResponse) extends ApiResponse
case class ErrorResponse(apiError: ApiError) extends ApiResponse

object SuccessResponse {
  implicit val writes: OWrites[SuccessResponse] = Json.writes[SuccessResponse]
}

object ErrorResponse {
  def simple(error: UktrError):                           ErrorResponse = ErrorResponse(SimpleError(error))
  def detailed(error: UktrBusinessValidationErrorDetail): ErrorResponse = ErrorResponse(DetailedError(error))

  implicit val writes: Writes[ErrorResponse] = Writes { response =>
    Json.toJson(response.apiError)
  }
}

object ApiResponse {
  implicit val writes: Writes[ApiResponse] = Writes {
    case s: SuccessResponse => Json.toJson(s)
    case e: ErrorResponse   => Json.toJson(e)
  }
}
