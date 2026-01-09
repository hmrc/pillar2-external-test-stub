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

  def DttIirUtprResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR Pillar 2 UKTR DTT",
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 3100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(3100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UKTR Pillar 2 UKTR DTT",
          chargeRefNo = None,
          amount = 3100,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR Pillar 2 UKTR IIR",
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 100,
      outstandingAmount = BigDecimal(50).some,
      clearedAmount = BigDecimal(50).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UKTR Pillar 2 UKTR IIR",
          chargeRefNo = None,
          amount = 50,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR Pillar 2 UKTR UTPR",
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 100,
      outstandingAmount = BigDecimal(100).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def AccruedInterestResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Determination Pillar 2 Determination IIR",
      chargeRefNo = "XDT3456789055".some,
      originalAmount = 3100,
      outstandingAmount = BigDecimal(3100).some,
      clearedAmount = None,
      clearingDetails = None,
      accruedInterest = BigDecimal(35).some
    )
  )

  def DttIirUtprInterestResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR Interest UKTR DTT interest",
      chargeRefNo = "XIN3456789642".some,
      originalAmount = 350,
      outstandingAmount = BigDecimal(150).some,
      clearedAmount = BigDecimal(200).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UKTR Interest UKTR DTT interest",
          chargeRefNo = None,
          amount = 200,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR Interest UKTR IIR interest",
      chargeRefNo = "XIN3456789642".some,
      originalAmount = 150,
      outstandingAmount = None,
      clearedAmount = BigDecimal(150).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UKTR Interest UKTR IIR interest",
          chargeRefNo = None,
          amount = 150,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR Interest UKTR UTPR interest",
      chargeRefNo = "XIN3556789642".some,
      originalAmount = 35,
      outstandingAmount = BigDecimal(35).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def DttIirUtprDeterminationResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Determination Pillar 2 Determination DTT",
      chargeRefNo = "XDT3556789012".some,
      originalAmount = 310000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(310000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Determination Pillar 2 Determination DTT",
          chargeRefNo = None,
          amount = 310000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Determination Pillar 2 Determination IIR",
      chargeRefNo = "XDT3556789012".some,
      originalAmount = 21250,
      outstandingAmount = BigDecimal(10000).some,
      clearedAmount = BigDecimal(11250).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Determination Pillar 2 Determination IIR",
          chargeRefNo = None,
          amount = 11250,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Determination Pillar 2 Determination UTPR",
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 1125,
      outstandingAmount = BigDecimal(1000).some,
      clearedAmount = BigDecimal(125).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Determination Pillar 2 Determination UTPR",
          chargeRefNo = None,
          amount = 125,
          clearingReason = "Cleared by Payment"
        )
      ).some
    )
  )

  def DttIirUtprDiscoveryResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Discovery Assessment Pillar 2 Discovery Assmt DTT",
      chargeRefNo = "XD23456789779".some,
      originalAmount = 350000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(350000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Discovery Assessment Pillar 2 Discovery Assmt DTT",
          chargeRefNo = None,
          amount = 350000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Discovery Assessment Pillar 2 Discovery Assmt IIR",
      chargeRefNo = "XD23456789779".some,
      originalAmount = 3000,
      outstandingAmount = BigDecimal(2000).some,
      clearedAmount = BigDecimal(1000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Discovery Assessment Pillar 2 Discovery Assmt IIR",
          chargeRefNo = None,
          amount = 1000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Discovery Assessment Pillar 2 Discovery Assmt UTPR",
      chargeRefNo = "XD23456789779".some,
      originalAmount = 3000,
      outstandingAmount = BigDecimal(2000).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def DttIirUtprOverpaidClaimResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Overpaid Claim Assmt Pillar2 Overpaid Cl Assmt DTT",
      chargeRefNo = "XOC3456789456".some,
      originalAmount = 6100,
      outstandingAmount = BigDecimal(6100).some,
      clearedAmount = None,
      clearingDetails = None,
      standOverAmount = BigDecimal(6100).some,
      appealFlag = true.some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Overpaid Claim Assmt Pillar2 Overpaid Cl Assmt IIR",
      chargeRefNo = "XOC3456789456".some,
      originalAmount = 5000,
      outstandingAmount = BigDecimal(2500).some,
      clearedAmount = BigDecimal(2500).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Overpaid Claim Assmt Pillar2 Overpaid Cl Assmt IIR",
          chargeRefNo = None,
          amount = 2500,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Overpaid Claim Assmt Pillar2 Overpaid Cl Assmt UTPR",
      chargeRefNo = "XOC3456789456".some,
      originalAmount = 4000,
      outstandingAmount = BigDecimal(4000).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def UktrDttMttLateFilingPenaltyResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR DTT LFP Pillar 2 UKTR DTT LFP",
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 UKTR DTT LFP Pillar 2 UKTR DTT LFP",
          chargeRefNo = None,
          amount = 100,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 UKTR MTT LFP Pillar 2 UKTR MTT LFP",
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = BigDecimal(100).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def OrnGirDttUktrMttLateFilingPenaltyResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 ORN/GIR DTT LFP Pillar 2 ORN/GIR DTT LFP",
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 ORN/GIR DTT LFP Pillar 2 ORN/GIR DTT LFP",
          chargeRefNo = None,
          amount = 100,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 ORN/GIR MTT LFP Pillar 2 ORN/GIR MTT LFP",
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = BigDecimal(50).some,
      clearedAmount = BigDecimal(50).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 ORN/GIR MTT LFP Pillar 2 ORN/GIR MTT LFP",
          chargeRefNo = None,
          amount = 50,
          clearingReason = "Cleared by Payment"
        )
      ).some
    )
  )

  def PotentialLostRevenuePenaltyResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Potential Lost Rev Pen",
      chargeRefNo = "XIN3456789011".some,
      originalAmount = 45000,
      outstandingAmount = BigDecimal(15000).some,
      clearedAmount = BigDecimal(30000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Potential Lost Rev Pen",
          chargeRefNo = None,
          amount = 30000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    )
  )

  def Schedule36PenaltyResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Sch 36 penalty",
      chargeRefNo = "XIN3456789444".some,
      originalAmount = 3500,
      outstandingAmount = BigDecimal(500).some,
      clearedAmount = BigDecimal(3000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Sch 36 penalty",
          chargeRefNo = None,
          amount = 3000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    )
  )

  def RecordKeepingPenaltyResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = "Pillar 2 Record Keeping Pen Pillar 2 Record Keeping Pen",
      chargeRefNo = "XIN3456789898".some,
      originalAmount = 3500,
      outstandingAmount = None,
      clearedAmount = BigDecimal(3500).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Pillar 2 Record Keeping Pen Pillar 2 Record Keeping Pen",
          chargeRefNo = None,
          amount = 3500,
          clearingReason = "Cleared by Payment"
        )
      ).some
    )
  )

  def RepaymentCreditResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Credit,
      transactionDesc = "Pillar 2 UKTR Repayment Pillar 2 UKTR Repayment",
      chargeRefNo = "XR23456789014".some,
      originalAmount = -10000,
      outstandingAmount = BigDecimal(-10000).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def InterestRepaymentCreditResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Credit,
      transactionDesc = "Pillar 2 UKTR RPI Pillar 2 UKTR RPI",
      chargeRefNo = "XR23456789000".some,
      originalAmount = -100,
      outstandingAmount = BigDecimal(-100).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  private def transactionJson(
    transactionType:   TransactionType,
    transactionDesc:   String,
    chargeRefNo:       Option[String],
    originalAmount:    BigDecimal,
    outstandingAmount: Option[BigDecimal],
    clearedAmount:     Option[BigDecimal],
    clearingDetails:   Option[Seq[ClearingJson]],
    standOverAmount:   Option[BigDecimal] = None,
    appealFlag:        Option[Boolean] = None,
    accruedInterest:   Option[BigDecimal] = None
  ): TransactionJson = JsObject(
    Seq(
      ("transactionType" -> JsString(transactionType.raw)).some,
      ("transactionDesc" -> JsString(transactionDesc)).some,
      Option.when(transactionType == TransactionType.Debit)("startDate" -> summon[Writes[LocalDate]].writes(currentYearStart)),
      Option.when(transactionType == TransactionType.Debit)("endDate"   -> summon[Writes[LocalDate]].writes(currentYearEnd)),
      accruedInterest.map("accruedInterest"                             -> JsNumber(_)),
      chargeRefNo.map("chargeRefNo"                                     -> JsString(_)),
      ("transactionDate" -> summon[Writes[LocalDate]].writes(today)).some,
      ("dueDate"         -> summon[Writes[LocalDate]].writes(currentYearEnd.plusMonths(dueDateBuffer))).some,
      ("originalAmount"  -> JsNumber(originalAmount)).some,
      outstandingAmount.map("outstandingAmount" -> JsNumber(_)),
      clearedAmount.map("clearedAmount"         -> JsNumber(_)),
      clearingDetails.map("clearingDetails"     -> JsArray(_)),
      standOverAmount.map("standOverAmount"     -> JsNumber(_)),
      appealFlag.map("appealFlag"               -> JsBoolean(_))
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
      "transactionDetails" -> transactions
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
