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

import cats.data.NonEmptyChain
import cats.implicits.toFoldableOps
import play.api.libs.json._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.UktrErrorCodes
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationError

import java.time.LocalDate

trait UktrSubmission {
  val accountingPeriodFrom: LocalDate
  val accountingPeriodTo:   LocalDate
  val obligationMTT:        Boolean
  val electionUKGAAP:       Boolean
  val liabilities:          Liability
}

object UktrSubmission {
  val UKTR_STUB_PROCESSING_DATE = "2022-01-31T09:26:17Z"

  def isLocalDate(date: Any): Boolean = date match {
    case _: java.time.LocalDate => true
    case _ => false
  }

  implicit val uktrSubmissionReads: Reads[UktrSubmission] = (json: JsValue) =>
    if ((json \ "liabilities" \ "returnType").isEmpty) {
      json.validate[UktrSubmissionData]
    } else {
      json.validate[UktrSubmissionNilReturn]
    }
}

case class UktrSubmissionError(errorCode: String, field: String, errorMessage: String) extends ValidationError

object UktrSubmissionErrorJsonConverter {
  def convertTo422JsonErrorFormat(errors: NonEmptyChain[ValidationError]): JsObject =
    Json.obj(
      "error" -> errors.toList.headOption.map(error =>
        Json.obj(
          "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
          "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
          "text"           -> error.errorMessage
        )
      )
    )
}
