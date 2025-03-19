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
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{generateChargeReference, generateFormBundleNumber, nowZonedDateTime}

case class LiabilityReturnSuccess(processingDate: String, formBundleNumber: String, chargeReference: String)

object LiabilityReturnSuccess {
  implicit val format: OFormat[LiabilityReturnSuccess] = Json.format[LiabilityReturnSuccess]

  def successfulUKTRResponse(chargeReference: Option[String] = None): LiabilitySuccessResponse =
    LiabilitySuccessResponse(
      LiabilityReturnSuccess(
        processingDate = nowZonedDateTime,
        formBundleNumber = generateFormBundleNumber(),
        chargeReference = chargeReference.fold(generateChargeReference())(identity)
      )
    )
}

case class NilReturnSuccess(processingDate: String, formBundleNumber: String)

object NilReturnSuccess {
  implicit val format: OFormat[NilReturnSuccess] = Json.format[NilReturnSuccess]

  def successfulNilReturnResponse: NilSuccessResponse = NilSuccessResponse(
    NilReturnSuccess(
      processingDate = nowZonedDateTime,
      formBundleNumber = generateFormBundleNumber()
    )
  )
}
