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

  import TransactionDesc.*

  def DTTChargeResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = UktrDttDesc,
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
        transactionDesc = UktrDttDesc,
        chargeRefNo = "X123456789012".some,
        originalAmount = 10000,
        outstandingAmount = None,
        clearedAmount = BigDecimal(10000).some,
        clearingDetails = Seq(
          clearingJson(
            transactionDesc = PaymentOnAccountDesc,
            chargeRefNo = None,
            amount = 10000,
            clearingReason = "Cleared by Payment"
          )
        ).some
      ),
      transactionJson(
        transactionType = TransactionType.Payment,
        transactionDesc = PaymentOnAccountDesc,
        chargeRefNo = None,
        originalAmount = -10000,
        outstandingAmount = None,
        clearedAmount = BigDecimal(-10000).some,
        clearingDetails = Seq(
          clearingJson(
            transactionDesc = UktrDttDesc,
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
      transactionDesc = UktrDttDesc,
      chargeRefNo = "X123456789012".some,
      originalAmount = 10000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(10000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 5000,
          clearingReason = "Cleared by Payment"
        ),
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 5000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Payment,
      transactionDesc = PaymentOnAccountDesc,
      chargeRefNo = None,
      originalAmount = -5000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(-5000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = UktrDttDesc,
          chargeRefNo = "X123456789012".some,
          amount = 5000,
          clearingReason = "Allocated to Charge"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Payment,
      transactionDesc = PaymentOnAccountDesc,
      chargeRefNo = None,
      originalAmount = -5000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(-5000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = UktrDttDesc,
          chargeRefNo = "X123456789012".some,
          amount = 5000,
          clearingReason = "Allocated to Charge"
        )
      ).some
    )
  )

  def RepaymentInterestResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Credit,
      transactionDesc = RepaymentInterestDesc,
      chargeRefNo = "XR23456789012".some,
      originalAmount = -100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(-100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = UktrDttDesc,
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
      transactionDesc = DeterminationDttDesc,
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
      transactionDesc = UktrDttDesc,
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 3100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(3100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 3100,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = UktrMttIirDesc,
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 100,
      outstandingAmount = BigDecimal(50).some,
      clearedAmount = BigDecimal(50).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 50,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = UktrMttUtprDesc,
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
      transactionDesc = DeterminationMttIirDesc,
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
      transactionDesc = LateUktrPayIntDttDesc,
      chargeRefNo = "XIN3456789642".some,
      originalAmount = 350,
      outstandingAmount = BigDecimal(150).some,
      clearedAmount = BigDecimal(200).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 200,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = LateUktrPayIntMttIirDesc,
      chargeRefNo = "XIN3456789642".some,
      originalAmount = 150,
      outstandingAmount = None,
      clearedAmount = BigDecimal(150).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 150,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = LateUktrPayIntMttUtprDesc,
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
      transactionDesc = DeterminationDttDesc,
      chargeRefNo = "XDT3556789012".some,
      originalAmount = 310000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(310000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 310000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = DeterminationMttIirDesc,
      chargeRefNo = "XDT3556789012".some,
      originalAmount = 21250,
      outstandingAmount = BigDecimal(10000).some,
      clearedAmount = BigDecimal(11250).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 11250,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = DeterminationMttUtprDesc,
      chargeRefNo = "XDT3456789012".some,
      originalAmount = 1125,
      outstandingAmount = BigDecimal(1000).some,
      clearedAmount = BigDecimal(125).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
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
      transactionDesc = DiscoveryDttDesc,
      chargeRefNo = "XD23456789779".some,
      originalAmount = 350000,
      outstandingAmount = None,
      clearedAmount = BigDecimal(350000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 350000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = DiscoveryMttIirDesc,
      chargeRefNo = "XD23456789779".some,
      originalAmount = 3000,
      outstandingAmount = BigDecimal(2000).some,
      clearedAmount = BigDecimal(1000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 1000,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = DiscoveryMttUtprDesc,
      chargeRefNo = "XD23456789779".some,
      originalAmount = 3000,
      outstandingAmount = BigDecimal(3000).some,
      clearedAmount = None,
      clearingDetails = None
    )
  )

  def DttIirUtprOverpaidClaimResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = OverpaidClaimDttDesc,
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
      transactionDesc = OverpaidClaimMttIirDesc,
      chargeRefNo = "XOC3456789456".some,
      originalAmount = 5000,
      outstandingAmount = BigDecimal(2500).some,
      clearedAmount = BigDecimal(2500).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 2500,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = OverpaidClaimMttUtprDesc,
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
      transactionDesc = LateUktrSubPenDttDesc,
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 100,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = LateUktrSubPenMttDesc,
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
      transactionDesc = LateOrnGirSubPenDttDesc,
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = None,
      clearedAmount = BigDecimal(100).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 100,
          clearingReason = "Cleared by Payment"
        )
      ).some
    ),
    transactionJson(
      transactionType = TransactionType.Debit,
      transactionDesc = LateOrnGirSubPenMttDesc,
      chargeRefNo = None,
      originalAmount = 100,
      outstandingAmount = BigDecimal(50).some,
      clearedAmount = BigDecimal(50).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
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
      transactionDesc = Schedule24Desc,
      chargeRefNo = "XIN3456789011".some,
      originalAmount = 45000,
      outstandingAmount = BigDecimal(15000).some,
      clearedAmount = BigDecimal(30000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
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
      transactionDesc = Schedule36Desc,
      chargeRefNo = "XIN3456789444".some,
      originalAmount = 3500,
      outstandingAmount = BigDecimal(500).some,
      clearedAmount = BigDecimal(3000).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
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
      transactionDesc = RecordKeepingPenDesc,
      chargeRefNo = "XIN3456789898".some,
      originalAmount = 3500,
      outstandingAmount = None,
      clearedAmount = BigDecimal(3500).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = PaymentOnAccountDesc,
          chargeRefNo = None,
          amount = 3500,
          clearingReason = "Cleared by Payment"
        )
      ).some
    )
  )

  def RepaymentCreditResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Payment,
      transactionDesc = PaymentOnAccountDesc,
      chargeRefNo = "XR23456789014".some,
      originalAmount = -500,
      outstandingAmount = BigDecimal(0).some,
      clearedAmount = BigDecimal(-500).some,
      clearingDetails = Seq(
        clearingJson(
          transactionDesc = "Repayment",
          chargeRefNo = None,
          amount = 500,
          clearingReason = "Outgoing payment - Paid"
        )
      ).some
    )
  )

  def InterestRepaymentCreditResponse: JsObject = responseWrapper(
    transactionJson(
      transactionType = TransactionType.Credit,
      transactionDesc = "Repayment interest - UKTR",
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
      Option.when(transactionType == TransactionType.Debit)("dueDate" -> summon[Writes[LocalDate]].writes(currentYearEnd.plusMonths(dueDateBuffer))),
      ("originalAmount" -> JsNumber(originalAmount)).some,
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
    case Debit extends TransactionType("DEBIT")
    case Credit extends TransactionType("CREDIT")
    case Payment extends TransactionType("PAYMENT")
  }

}

/** Transaction descriptions condensed to ≤30 chars for ETMP SAP limit */
private enum TransactionDesc(val description: String) {
  // Payment
  case PaymentOnAccountDesc extends TransactionDesc("Pillar 2 Payment on Account")

  // UKTR charges
  case UktrDttDesc extends TransactionDesc("UKTR - DTT")
  case UktrMttIirDesc extends TransactionDesc("UKTR - MTT (IIR)")
  case UktrMttUtprDesc extends TransactionDesc("UKTR - MTT (UTPR)")

  // Repayment interest
  case RepaymentInterestDesc extends TransactionDesc("Repayment interest - UKTR")

  // Determination charges
  case DeterminationDttDesc extends TransactionDesc("Determination - DTT")
  case DeterminationMttIirDesc extends TransactionDesc("Determination - MTT (IIR)")
  case DeterminationMttUtprDesc extends TransactionDesc("Determination - MTT (UTPR)")

  // Determination interest
  case DeterminationIntDttDesc extends TransactionDesc("Determination interest - DTT")
  case DeterminationIntMttIirDesc extends TransactionDesc("Determination int- MTT (IIR)")
  case DeterminationIntMttUtprDesc extends TransactionDesc("Determination int - MTT (UTPR)")

  // Discovery Assessment charges
  case DiscoveryDttDesc extends TransactionDesc("Discovery Assessment - DTT")
  case DiscoveryMttIirDesc extends TransactionDesc("Discovery Assessment-MTT(IIR)")
  case DiscoveryMttUtprDesc extends TransactionDesc("Discovery Assessment-MTT(UTPR)")

  // Discovery Assessment interest
  case DiscoveryIntDttDesc extends TransactionDesc("Discovery Assessment int - DTT")
  case DiscoveryIntMttIirDesc extends TransactionDesc("Discovery Assmnt int-MTT(IIR)")
  case DiscoveryIntMttUtprDesc extends TransactionDesc("Discovery Assmnt int-MTT(UTPR)")

  // Overpaid Claim Assessment charges
  case OverpaidClaimDttDesc extends TransactionDesc("Overpaid claim assmnt - DTT")
  case OverpaidClaimMttIirDesc extends TransactionDesc("O/paid claim assmnt-MTT (IIR)")
  case OverpaidClaimMttUtprDesc extends TransactionDesc("O/paid claim assmnt-MTT (UTPR)")

  // Overpaid Claim Assessment interest
  case OverpaidClaimIntDttDesc extends TransactionDesc("O/paid claim assmnt int - DTT")
  case OverpaidClaimIntMttIirDesc extends TransactionDesc("O/p claim assmnt int-MTT(IIR)")
  case OverpaidClaimIntMttUtprDesc extends TransactionDesc("O/p claim assmnt int-MTT(UTPR)")

  // Late UKTR payment interest
  case LateUktrPayIntDttDesc extends TransactionDesc("Late UKTR pay int - DTT")
  case LateUktrPayIntMttIirDesc extends TransactionDesc("Late UKTR pay int - MTT(IIR)")
  case LateUktrPayIntMttUtprDesc extends TransactionDesc("Late UKTR pay int - MTT(UTPR)")

  // Late submission penalties - UKTR
  case LateUktrSubPenDttDesc extends TransactionDesc("Late UKTR sub pen - DTT")
  case LateUktrSubPenDtt3MnthDesc extends TransactionDesc("Late UKTR sub pen - DTT 3mnth")
  case LateUktrSubPenDtt6MnthDesc extends TransactionDesc("Late UKTR sub pen - DTT 6mnth")
  case LateUktrSubPenDtt12MnthDesc extends TransactionDesc("Late UKTR sub pen - DTT 12mnth")
  case LateUktrSubPenMttDesc extends TransactionDesc("Late UKTR sub pen - MTT")
  case LateUktrSubPenMtt3MnthDesc extends TransactionDesc("Late UKTR sub pen - MTT 3mnth")
  case LateUktrSubPenMtt6MnthDesc extends TransactionDesc("Late UKTR sub pen - MTT 6mnth")
  case LateUktrSubPenMtt12MnthDesc extends TransactionDesc("Late UKTR sub pen - MTT 12mnth")

  // Late submission penalties - ORN/GIR
  case LateOrnGirSubPenDttDesc extends TransactionDesc("Late ORN/GIR sub pen - DTT")
  case LateOrnGirSubPenDtt3MnthDesc extends TransactionDesc("Late ORN/GIR sub pen -DTT3mnth")
  case LateOrnGirSubPenDtt6MnthDesc extends TransactionDesc("Late ORN/GIR sub pen -DTT6mnth")
  case LateOrnGirSubPenMttDesc extends TransactionDesc("Late ORN/GIR sub pen - MTT")
  case LateOrnGirSubPenMtt3MnthDesc extends TransactionDesc("Late ORN/GIR sub pen-MTT 3mnth")
  case LateOrnGirSubPenMtt6MnthDesc extends TransactionDesc("Late ORN/GIR sub pen-MTT 6mnth")

  // Other penalties
  case Schedule36Desc extends TransactionDesc("Schedule 36 information notice")
  case Schedule24Desc extends TransactionDesc("Schedule 24 inaccurate return")
  case RecordKeepingPenDesc extends TransactionDesc("Accurate records failure pen")
  case GaarPenaltyDesc extends TransactionDesc("General Anti Abuse Rule pen")
}

private object TransactionDesc {
  given Conversion[TransactionDesc, String] = _.description
}
