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

import play.api.libs.json._
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationError

import java.time.LocalDate

trait UKTRSubmission extends BaseSubmission {
  val accountingPeriodFrom: LocalDate
  val accountingPeriodTo:   LocalDate
  val obligationMTT:        Boolean
  val electionUKGAAP:       Boolean
  val liabilities:          Liabilities
}

object UKTRSubmission {

  implicit val formatUKTRSubmission: Format[UKTRSubmission] = new Format[UKTRSubmission] {
    override def reads(json: JsValue): JsResult[UKTRSubmission] =
      if ((json \ "liabilities" \ "returnType").isDefined) {
        Json.fromJson[UKTRNilReturn](json)
      } else {
        Json.fromJson[UKTRLiabilityReturn](json)
      }

    override def writes(o: UKTRSubmission): JsValue = o match {
      case nil:       UKTRNilReturn       => Json.toJson(nil)(Json.format[UKTRNilReturn])
      case liability: UKTRLiabilityReturn => Json.toJson(liability)(Json.format[UKTRLiabilityReturn])
      case _ => throw new IllegalStateException("Unknown UKTRSubmission type")
    }
  }
}

case class UKTRSubmissionError(error: ETMPError) extends ValidationError {
  override def errorCode:    String = error.code
  override def errorMessage: String = error.message
}
