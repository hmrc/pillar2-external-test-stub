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

import java.time.{ZoneOffset, ZonedDateTime}

sealed trait UKTRSuccessResponse

object UKTRSuccessResponse {
  implicit val writes: Writes[UKTRSuccessResponse] = {
    case liabilityReturn: SubmitUKTRSuccessResponse    => Json.toJson(liabilityReturn)(SubmitUKTRSuccessResponse.writes)
    case nilReturn:       UKTRNilReturnSuccessResponse => Json.toJson(nilReturn)(UKTRNilReturnSuccessResponse.writes)
    case _ => throw new IllegalStateException(s"Unknown UKTRSuccessResponse type")
  }
}

case class SubmitUKTRSuccessResponse(
  processingDate:   ZonedDateTime,
  formBundleNumber: String,
  chargeReference:  String
) extends UKTRSuccessResponse

object SubmitUKTRSuccessResponse {
  implicit val writes: OWrites[SubmitUKTRSuccessResponse] = Json.writes[SubmitUKTRSuccessResponse]

  def successfulUKTRResponse: ApiResponse =
    SuccessResponse(
      SubmitUKTRSuccessResponse(
        processingDate = ZonedDateTime.now(ZoneOffset.UTC),
        formBundleNumber = "119000004320",
        chargeReference = "XTC01234123412"
      )
    )
}

case class UKTRNilReturnSuccessResponse(
  processingDate:   ZonedDateTime,
  formBundleNumber: String
) extends UKTRSuccessResponse

object UKTRNilReturnSuccessResponse {
  implicit val writes: OWrites[UKTRNilReturnSuccessResponse] = Json.writes[UKTRNilReturnSuccessResponse]

  def successfulNilReturnResponse: ApiResponse =
    SuccessResponse(
      UKTRNilReturnSuccessResponse(
        processingDate = ZonedDateTime.now(ZoneOffset.UTC),
        formBundleNumber = "119000004320"
      )
    )
}
