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

package uk.gov.hmrc.pillar2externalteststub.helpers

import cats.syntax.option.given
import play.api.libs.json.*

import java.time.{Clock, LocalDate, ZonedDateTime}
import javax.inject.Inject

class AccountActivityDataResponses @Inject() (clock: Clock) {

  private def today            = LocalDate.now
  private def currentYearStart = LocalDate.of(today.getYear, 1, 1)
  private def currentYearEnd   = LocalDate.of(today.getYear, 12, 31)
  private val dueDateBuffer    = 6

  def DTTChargeResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
      chargeRefNo = "X123456789012".some,
      originalAmount = 10000,
      outstandingAmount = BigDecimal(10000).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def FullyPaidChargeResponse: JsObject =
    responseWrapper(
      transactionJson(
        transactionType = TransactionType.Debit,
        transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
        chargeRefNo = "X123456789012".some,
        originalAmount = 10000,
        outstandingAmount = BigDecimal(10000).some,
        clearedAmount = BigDecimal(10000).some,
        clearingDetails = Seq(
          clearingJson(
            transactionDesc = "On Account Pillar 2 (Payment on Account)",
            chargeRefNo = None,
            amount = 10000,
            clearingReason = "Cleared by Payment"
          )
        ).some
      ),
      transactionJson(
        transactionType = TransactionType.Payment,
        transactionDesc = "On Account Pillar 2 (Payment on Account)",
        chargeRefNo = None,
        originalAmount = 10000,
        outstandingAmount = None,
        clearedAmount = BigDecimal(10000).some,
        clearingDetails = Seq(
          clearingJson(
            transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
            chargeRefNo = "X123456789012".some,
            amount = 10000,
            clearingReason = "Allocated to Charge"
          )
        ).some
      )
    )

  def FullyPaidChargeWithSplitPaymentsResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
      chargeRefNo = "X123456789012".some,
      originalAmount = 10000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(10000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "On Account Pillar 2 (Payment on Account)",
          chargeRefNo = None,
          amount = 5000,
          clearingReason = "Cleared by Payment"
        ),
        clearingJson(
          transactionDesc = "On Account Pillar 2 (Payment on Account)",
          chargeRefNo = None,
          amount = 5000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Payment,
      transactionDesc = "On Account Pillar 2 (Payment on Account)",
      chargeRefNo = None,
      originalAmount = 5000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(5000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
          chargeRefNo = "X123456789012".some,
          amount = 10000,
          clearingReason = "Allocated to Charge"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Payment,
      transactionDesc = "On Account Pillar 2 (Payment on Account)",
      chargeRefNo = None,
      originalAmount = 5000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(5000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
          chargeRefNo = "X123456789012".some,
          amount = 10000,
          clearingReason = "Allocated to Charge"
        )
      ).some
    )
  )

  def RepaymentInterestResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Credit,
      transactionDesc = "Pillar 2 UKTR RPI Pillar 2 OECD RPI",
      chargeRefNo = "XR23456789012".some,
      originalAmount = -100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(-100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UK Tax Return Pillar 2 DTT",
          chargeRefNo = "X123456789012".some,
          amount = 100,
          clearingReason = "Allocated to Charge"
        )
      ).some
    )
  )

  def DTTDeterminationResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Determination Pillar 2 DTT",
      chargeRefNo = "XDT3456789698".some,
      originalAmount = 10000,
      outstandingAmount = BigDecimal(10000).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  Json.obj(
    "success" -> Json.obj(
      "processingDate" -> ZonedDateTime.now(clock),
      "transactionDetails" -> Json.arr(
        Json.obj(
          "transactionType"   -> "Debit",
          "transactionDesc"   -> "Pillar 2 Determination Pillar 2 DTT",
          "startDate"         -> currentYearStart,
          "endDate"           -> currentYearEnd,
          "chargeRefNo"       -> "XDT3456789698",
          "transactionDate"   -> today,
          "dueDate"           -> currentYearEnd.plusMonths(dueDateBuffer),
          "originalAmount"    -> 10000,
          "outstandingAmount" -> 10000
        )
      )
    )
  )

  private def transactionJson(
    transactionType:   TransactionType,
    transactionDesc:   String,
    chargeRefNo:       Option[String],
    originalAmount:    BigDecimal,
    outstandingAmount: Option[BigDecimal],
    clearedAmount:     Option[BigDecimal],
    clearingDetails:   Option[Seq[ClearingJson]]
  ): TransactionJson = JsObject(
    Seq(
      ("transactionType" -> JsString(transactionType.raw)).some,
      ("transactionDesc" -> JsString(transactionDesc)).some,
      Option.when(transactionType == TransactionType.Debit)("startDate" -> summon[Writes[LocalDate]].writes(currentYearStart)),
      Option.when(transactionType == TransactionType.Debit)("endDate"   -> summon[Writes[LocalDate]].writes(currentYearEnd)),
      chargeRefNo.map("chargeRefNo"                                     -> JsString(_)),
      ("transactionDate" -> summon[Writes[LocalDate]].writes(today)).some,
      ("dueDate"         -> summon[Writes[LocalDate]].writes(currentYearEnd.plusMonths(dueDateBuffer))).some,
      ("originalAmount"  -> JsNumber(originalAmount)).some,
      outstandingAmount.map("outstandingAmount" -> JsNumber(_)),
      clearedAmount.map("clearedAmount"         -> JsNumber(_)),
      clearingDetails.map("clearingDetails"     -> JsArray(_))
    ).flatten
  )

  private def clearingJson(
    transactionDesc: String,
    chargeRefNo:     Option[String],
    amount:          BigDecimal,
    clearingReason:  String
  ): ClearingJson = JsObject(
    Seq(
      ("transactionDesc" -> JsString(transactionDesc)).some,
      chargeRefNo.map("chargeRefNo" -> JsString(_)),
      ("dueDate"        -> summon[Writes[LocalDate]].writes(currentYearEnd.plusMonths(dueDateBuffer))).some,
      ("amount"         -> JsNumber(amount)).some,
      ("clearingDate"   -> summon[Writes[LocalDate]].writes(today)).some,
      ("clearingReason" -> JsString(clearingReason)).some
    ).flatten
  )

  private def responseWrapper(transactions: TransactionJson*): JsObject = Json.obj(
    "success" -> Json.obj(
      "processingDate"     -> ZonedDateTime.now(clock),
      "transactionDetails" -> Json.arr(transactions)
    )
  )

  private type TransactionJson = JsObject
  private type ClearingJson    = JsObject

  private enum TransactionType(val raw: String) {
    case Debit extends TransactionType("Debit")
    case Credit extends TransactionType("Credit")
    case Payment extends TransactionType("Payment")
  }

}
