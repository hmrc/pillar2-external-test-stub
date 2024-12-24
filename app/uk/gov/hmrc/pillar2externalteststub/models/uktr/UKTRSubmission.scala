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
import play.api.mvc.Result
import play.api.mvc.Results.UnprocessableEntity
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.UKTRBusinessValidationErrorDetail.nowZonedDateTime
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationError

import java.time.LocalDate
import scala.concurrent.Future

trait UKTRSubmission {
  val accountingPeriodFrom: LocalDate
  val accountingPeriodTo:   LocalDate
  val obligationMTT:        Boolean
  val electionUKGAAP:       Boolean
  val liabilities:          Liabilities
}

object UKTRSubmission {

  implicit val uktrSubmissionReads: Reads[UKTRSubmission] = (json: JsValue) =>
    if ((json \ "liabilities" \ "returnType").isEmpty) {
      json.validate[UKTRSubmissionData]
    } else {
      json.validate[UKTRSubmissionNilReturn]
    }
}

case class UKTRSubmissionError(errorCode: String, field: String, errorMessage: String) extends ValidationError

object UKTRErrorTransformer {
  def from422ToJson(errors: NonEmptyChain[ValidationError]): Future[Result] =
    Future.successful(
      UnprocessableEntity(
        Json.obj(
          "errors" -> errors.toList.headOption.map(error =>
            Json.obj(
              "processingDate" -> nowZonedDateTime.toString,
              "code"           -> error.errorCode,
              "text"           -> error.errorMessage
            )
          )
        )
      )
    )
}
