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

import java.time.{LocalDate, LocalDateTime}

object AccountActivityDataResponses {

  private def today            = LocalDate.now
  private def currentYearStart = LocalDate.of(today.getYear, 1, 1)
  private def currentYearEnd   = LocalDate.of(today.getYear, 12, 31)
  private val dueDateBuffer    = 6

  def DTTChargeResponse(now: LocalDateTime): JsObject = Json.obj(
    "processingDate" -> now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType"   -> "Debit",
        "transactionDesc"   -> "Pillar 2 UK Tax Return Pillar 2 DTT",
        "startDate"         -> currentYearStart,
        "endDate"           -> currentYearEnd,
        "chargeRefNo"       -> "X123456789012",
        "transactionDate"   -> today,
        "dueDate"           -> currentYearEnd.plusMonths(dueDateBuffer),
        "originalAmount"    -> 10000,
        "outstandingAmount" -> 10000
      )
    )
  )

  def FullyPaidChargeResponse(now: LocalDateTime): JsObject = Json.obj(
    "processingDate" -> now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType" -> "Debit",
        "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
        "startDate"       -> currentYearStart,
        "endDate"         -> currentYearEnd,
        "chargeRefNo"     -> "X123456789012",
        "transactionDate" -> today,
        "dueDate"         -> currentYearEnd.plusMonths(dueDateBuffer),
        "originalAmount"  -> 10000,
        "clearedAmount"   -> 10000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
            "amount"          -> 10000,
            "clearingDate"    -> today,
            "clearingReason"  -> "Cleared by Payment"
          )
        )
      ),
      Json.obj(
        "transactionType" -> "Payment",
        "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
        "transactionDate" -> today,
        "originalAmount"  -> 10000,
        "clearedAmount"   -> 10000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusMonths(dueDateBuffer),
            "amount"          -> 10000,
            "clearingDate"    -> today,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      )
    )
  )

  def FullyPaidChargeWithSplitPaymentsResponse(now: LocalDateTime): JsObject = Json.obj(
    "processingDate" -> now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType" -> "Debit",
        "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
        "startDate"       -> currentYearStart,
        "endDate"         -> currentYearEnd,
        "chargeRefNo"     -> "X123456789012",
        "transactionDate" -> today,
        "dueDate"         -> currentYearEnd.plusMonths(dueDateBuffer),
        "originalAmount"  -> 10000,
        "clearedAmount"   -> 10000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
            "amount"          -> 5000,
            "clearingDate"    -> today,
            "clearingReason"  -> "Cleared by Payment"
          ),
          Json.obj(
            "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
            "amount"          -> 5000,
            "clearingDate"    -> today,
            "clearingReason"  -> "Cleared by Payment"
          )
        )
      ),
      Json.obj(
        "transactionType" -> "Payment",
        "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
        "transactionDate" -> today,
        "originalAmount"  -> 5000,
        "clearedAmount"   -> 5000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusMonths(dueDateBuffer),
            "amount"          -> 5000,
            "clearingDate"    -> today,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      ),
      Json.obj(
        "transactionType" -> "Payment",
        "transactionDesc" -> "On Account Pillar 2 (Payment on Account)",
        "transactionDate" -> today,
        "originalAmount"  -> 5000,
        "clearedAmount"   -> 5000,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusMonths(dueDateBuffer),
            "amount"          -> 5000,
            "clearingDate"    -> today,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      )
    )
  )

  def RepaymentInterestResponse(now: LocalDateTime): JsObject = Json.obj(
    "processingDate" -> now,
    "transactionDetails" -> Json.arr(
      Json.obj(
        "transactionType" -> "Credit",
        "transactionDesc" -> "Pillar 2 UKTR RPI Pillar 2 OECD RPI",
        "chargeRefNo"     -> "XR23456789012",
        "transactionDate" -> today,
        "originalAmount"  -> -100,
        "clearedAmount"   -> -100,
        "clearingDetails" -> Json.arr(
          Json.obj(
            "transactionDesc" -> "Pillar 2 UK Tax Return Pillar 2 DTT",
            "chargeRefNo"     -> "X123456789012",
            "dueDate"         -> currentYearEnd.plusMonths(dueDateBuffer),
            "amount"          -> 100,
            "clearingDate"    -> today,
            "clearingReason"  -> "Allocated to Charge"
          )
        )
      )
    )
  )

  def DTTDeterminationResponse(now: LocalDateTime): JsObject = Json.obj(
    "processingDate" -> now,
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

}
