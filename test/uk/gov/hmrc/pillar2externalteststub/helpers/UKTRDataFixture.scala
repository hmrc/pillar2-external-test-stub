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

import org.bson.types.ObjectId
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission

import java.time.Instant

trait UKTRDataFixture extends Pillar2DataFixture with TestOrgDataFixture {

  val invalidUKTRAmounts: Seq[BigDecimal] = Seq(-5, 1e+13, 10.999)

  val validLiableEntity: JsObject = Json.obj(
    "ukChargeableEntityName" -> "New Company",
    "idType"                 -> "CRN",
    "idValue"                -> "1234",
    "amountOwedDTT"          -> 100,
    "amountOwedIIR"          -> 100,
    "amountOwedUTPR"         -> 100
  )

  val validRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
    "accountingPeriodTo"   -> accountingPeriod.endDate.toString,
    "obligationMTT"        -> false,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "electionDTTSingleMember"  -> false,
      "electionUTPRSingleMember" -> false,
      "numberSubGroupDTT"        -> 4,
      "numberSubGroupUTPR"       -> 5,
      "totalLiability"           -> 300,
      "totalLiabilityDTT"        -> 100,
      "totalLiabilityIIR"        -> 100,
      "totalLiabilityUTPR"       -> 100,
      "liableEntities"           -> Json.arr(validLiableEntity)
    )
  )

  def nilReturnBody(obligationMTT: Boolean, electionUKGAAP: Boolean): JsObject = Json.obj(
    "accountingPeriodFrom" -> accountingPeriod.startDate.toString,
    "accountingPeriodTo"   -> accountingPeriod.endDate.toString,
    "obligationMTT"        -> obligationMTT,
    "electionUKGAAP"       -> electionUKGAAP,
    "liabilities"          -> Json.obj("returnType" -> "NIL_RETURN")
  )

  val invalidLiableEntityukChargeableEntityNameZeroLength: JsObject = Json.obj(
    "ukChargeableEntityName" -> "",
    "idType"                 -> "UTR",
    "idValue"                -> "abc45678",
    "amountOwedDTT"          -> 100,
    "amountOwedIIR"          -> 100,
    "amountOwedUTPR"         -> 100
  )
  val invalidIdTypeZeroLength: JsObject = Json.obj(
    "ukChargeableEntityName" -> "New Company",
    "idType"                 -> "",
    "idValue"                -> "abc45678",
    "amountOwedDTT"          -> 100,
    "amountOwedIIR"          -> 100,
    "amountOwedUTPR"         -> 100
  )

  val missingUkChargeableEntityNameRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "idType"         -> "CRN",
              "idValue"        -> "12345678",
              "amountOwedDTT"  -> 100,
              "amountOwedIIR"  -> 100,
              "amountOwedUTPR" -> 100
            )
          )
        )
      )
  )

  val liabilitySubmission: UKTRSubmission = Json.fromJson[UKTRSubmission](validRequestBody).get
  val nilSubmission:       UKTRSubmission = Json.fromJson[UKTRSubmission](nilReturnBody(obligationMTT = false, electionUKGAAP = true)).get

  val missingUkChargeableEntNameLiableEntity2AndInvalidIdTypeLiableEnt3ReqBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            validLiableEntity,
            missingUkChargeableEntityNameRequestBody,
            invalidIdTypeZeroLength
          )
        )
      )
  )
  val missingIdTypeRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UKTR Newco PLC",
              "idValue"                -> "12345678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val missingIdValueRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UKTR Newco PLC",
              "idType"                 -> "CRN",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val missingAmountOwedDTTRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UKTR Newco PLC",
              "idType"                 -> "CRN",
              "idValue"                -> "12345678",
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val missingAmountOwedIIRRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UKTR Newco PLC",
              "idType"                 -> "CRN",
              "idValue"                -> "12345678",
              "amountOwedDTT"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val missingAmountOwedUTPRRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UKTR Newco PLC",
              "idType"                 -> "CRN",
              "idValue"                -> "12345678",
              "amountOwedIIR"          -> 100,
              "amountOwedDTT"          -> 100
            )
          )
        )
      )
  )
  val invalidUkChargeableEntityNameRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            invalidLiableEntityukChargeableEntityNameZeroLength
          )
        )
      )
  )
  val ukChargeableEntityNameTooLongRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "12345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901",
              "idType"         -> "CRN",
              "idValue"        -> "12345678",
              "amountOwedDTT"  -> 100,
              "amountOwedIIR"  -> 100,
              "amountOwedUTPR" -> 100
            )
          )
        )
      )
  )
  val invalidIdTypeZeroLengthRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "",
              "idValue"                -> "12345678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val invalidIdTypeRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "INVALID_ID_TYPE",
              "idValue"                -> "12345678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val invalidIdValueZeroLengthRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "CRN",
              "idValue"                -> "",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val invalidIdValueLengthExceeds15RequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc4567890123456",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )
  val invalidIdTypeEntity1AndInvalidIdValueEntity2RequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "INVALID_ID_TYPE",
              "idValue"                -> "12345678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            ),
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc4567890123456",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )

  val invalidAmountOwedIIREntity2AndInvalidAmountOwedUTPREntity3RequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc45678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            ),
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc45678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            ),
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc45678",
              "amountOwedDTT"          -> 100,
              "amountOwedIIR"          -> 100,
              "amountOwedUTPR"         -> 100
            )
          )
        )
      )
  )

  val invalidAccountingPeriodFromNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "x",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val missingAccountingPeriodFromNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodTo" -> "2024-12-14",
    "obligationMTT"      -> true,
    "electionUKGAAP"     -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val invalidAccountingPeriodToNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-12-14",
    "accountingPeriodTo"   -> "2025-02-31",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val missingAccountingPeriodToNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-12-14",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val invalidObligationMTTNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-12-14",
    "accountingPeriodTo"   -> "2025-02-03",
    "obligationMTT"        -> "x",
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val missingObligationMTTNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val invalidElectionUKGAAPNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-12-14",
    "accountingPeriodTo"   -> "2025-02-03",
    "obligationMTT"        -> false,
    "electionUKGAAP"       -> "Z",
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )
  val missingElectionUKGAAPNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-12-14",
    "accountingPeriodTo"   -> "2025-02-03",
    "obligationMTT"        -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )

  val invalidReturnTypeNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> "INVALID_NIL_RETURN"
    )
  )
  val emptyReturnTypeNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "returnType" -> ""
    )
  )

  val validGetByPillar2IdResponse: UKTRMongoSubmission = UKTRMongoSubmission(
    _id = new ObjectId(),
    pillar2Id = validPlrId,
    chargeReference = Some(chargeReference),
    data = Json.fromJson[UKTRSubmission](validRequestBody).get,
    submittedAt = Instant.now()
  )
}
