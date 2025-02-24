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
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.nowZonedDateTime
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationError

import java.time.LocalDate
import scala.concurrent.Future

trait UKTRSubmission {
  val accountingPeriodFrom: LocalDate
  val accountingPeriodTo:   LocalDate
  val obligationMTT:        Boolean
  val electionUKGAAP:       Boolean
  val liabilities:          Liabilities
  def isNilReturn: Boolean

  def isValidAccountingPeriod: Boolean = {
    val from = accountingPeriodFrom
    val to   = accountingPeriodTo
    !from.isAfter(to) &&
    from.getYear >= 1900 && from.getYear <= 9999 &&
    to.getYear >= 1900 && to.getYear <= 9999
  }
}

object UKTRSubmission {

  implicit val formatUKTRSubmission: Format[UKTRSubmission] = new Format[UKTRSubmission] {
    override def reads(json: JsValue): JsResult[UKTRSubmission] = {
      val result = if ((json \ "liabilities" \ "returnType").isDefined) {
        Json.fromJson[UKTRNilReturn](json)
      } else {
        Json.fromJson[UKTRLiabilityReturn](json)
      }

      result.flatMap { submission =>
        if (submission.isValidAccountingPeriod) JsSuccess(submission)
        else JsError("Invalid accounting period dates")
      }
    }

    override def writes(o: UKTRSubmission): JsValue = o match {
      case nil:       UKTRNilReturn       => Json.toJson(nil)(Json.format[UKTRNilReturn])
      case liability: UKTRLiabilityReturn => Json.toJson(liability)(Json.format[UKTRLiabilityReturn])
      case _ => throw new IllegalStateException("Unknown UKTRSubmission type")
    }
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
              "processingDate" -> nowZonedDateTime,
              "code"           -> error.errorCode,
              "text"           -> error.errorMessage
            )
          )
        )
      )
    )
}
