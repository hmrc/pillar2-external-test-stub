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

import play.api.libs.json.JsObject
import play.api.libs.json.Json

import java.time.LocalDate

object AccountActivityDataFixture {

  private val now              = LocalDate.now
  private val currentYearStart = LocalDate.of(now.getYear, 1, 1)
  private val currentYearEnd   = LocalDate.of(now.getYear, 12, 31)
  private val dueDateBuffer    = 181

  val SoleChargeResponse: JsObject = Json.obj(
    "processingDate" -> LocalDate.now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType"   -> "Debit",
        "transactionDesc"   -> "Pillar 2 UK Tax Return Pillar 2 DTT",
        "startDate"         -> currentYearStart,
        "endDate"           -> currentYearEnd,
        "chargeRefNo"       -> "X123456789012",
        "transactionDate"   -> now,
        "dueDate"           -> currentYearEnd.plusDays(dueDateBuffer),
        "originalAmount"    -> 10000,
        "outstandingAmount" -> 10000
      )
    )
  )

  val FullyPaidChargeResponse: JsObject = Json.obj(
    "processingDate" -> LocalDate.now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType" -> "Debit",
        "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
        "startDate"       -> currentYearStart,
        "endDate"         -> currentYearEnd,
        "chargeRefNo"     -> "X123456789012",
        "transactionDate" -> now,
        "dueDate"         -> currentYearEnd.plusDays(dueDateBuffer),
        "originalAmount"  -> 10000,
        "clearedAmount"   -> 10000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
            "amount"          -> 10000,
            "clearingDate"    -> now,
            "clearingReason"  -> "Cleared by Payment"
          )
        )
      ),
      Json.obj(
        "transactionType" -> "Payment",
        "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
        "transactionDate" -> now,
        "originalAmount"  -> 10000,
        "clearedAmount"   -> 10000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusDays(dueDateBuffer),
            "amount"          -> 10000,
            "clearingDate"    -> now,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      )
    )
  )

  val FullyPaidChargeWithSplitPaymentsResponse: JsObject = Json.obj(
    "processingDate" -> LocalDate.now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType" -> "Debit",
        "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
        "startDate"       -> currentYearStart,
        "endDate"         -> currentYearEnd,
        "chargeRefNo"     -> "X123456789012",
        "transactionDate" -> now,
        "dueDate"         -> currentYearEnd.plusDays(dueDateBuffer),
        "originalAmount"  -> 10000,
        "clearedAmount"   -> 10000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
            "amount"          -> 5000,
            "clearingDate"    -> now,
            "clearingReason"  -> "Cleared by Payment"
          ),
          Json.obj(
            "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
            "amount"          -> 5000,
            "clearingDate"    -> now,
            "clearingReason"  -> "Cleared by Payment"
          )
        )
      ),
      Json.obj(
        "transactionType" -> "Payment",
        "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
        "transactionDate" -> now,
        "originalAmount"  -> 5000,
        "clearedAmount"   -> 5000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusDays(dueDateBuffer),
            "amount"          -> 5000,
            "clearingDate"    -> now,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      ),
      Json.obj(
        "transactionType" -> "Payment",
        "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
        "transactionDate" -> now,
        "originalAmount"  -> 5000,
        "clearedAmount"   -> 5000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusDays(dueDateBuffer),
            "amount"          -> 5000,
            "clearingDate"    -> now,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      )
    )
  )

}
