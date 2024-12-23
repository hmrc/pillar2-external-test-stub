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

package uk.gov.hmrc.pillar2externalteststub.controllers

import org.scalatest.Inspectors.forAll
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.CREATED
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.UKTRErrorCodes

import java.time.format.DateTimeFormatter

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues {
  val authHeader:  (String, String)  = HeaderNames.authorisation -> "Bearer valid_token"
  val datePattern: DateTimeFormatter = DateTimeFormatter.ISO_DATE_TIME

  val invalidUKTRAmounts: Seq[BigDecimal] = Seq(-5, 1e+13, 10.999)

  val domesticOnlyPlrReference    = "XEPLR5555555555"
  val nonDomesticOnlyPlrReference = "XEPLR1234567890"

  def request(plrReference: String = domesticOnlyPlrReference, body: JsObject): FakeRequest[JsObject] =
    FakeRequest(POST, routes.SubmitUKTRController.submitUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrReference)
      .withBody(body)

  val validLiableEntity: JsObject = Json.obj(
    "ukChargeableEntityName" -> "New Company",
    "idType"                 -> "CRN",
    "idValue"                -> "1234",
    "amountOwedDTT"          -> 12345678901.0,
    "amountOwedIIR"          -> 1234567890.09,
    "amountOwedUTPR"         -> 600.50
  )

  val validRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> false,
    "electionUKGAAP"       -> false,
    "liabilities" -> Json.obj(
      "electionDTTSingleMember"  -> false,
      "electionUTPRSingleMember" -> false,
      "numberSubGroupDTT"        -> 4,
      "numberSubGroupUTPR"       -> 5,
      "totalLiability"           -> 10000.99,
      "totalLiabilityDTT"        -> 5000.99,
      "totalLiabilityIIR"        -> 4000,
      "totalLiabilityUTPR"       -> 10000.99,
      "liableEntities"           -> Json.arr(validLiableEntity)
    )
  )

  def nilReturnBody(obligationMTT: Boolean, electionUKGAAP: Boolean): JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> obligationMTT,
    "electionUKGAAP"       -> electionUKGAAP,
    "liabilities"          -> Json.obj("returnType" -> "NIL_RETURN")
  )

  val invalidLiableEntityukChargeableEntityNameZeroLength: JsObject = Json.obj(
    "ukChargeableEntityName" -> "",
    "idType"                 -> "UTR",
    "idValue"                -> "abc45678",
    "amountOwedDTT"          -> 12345678901.9,
    "amountOwedIIR"          -> 1234567890.01,
    "amountOwedUTPR"         -> 1234567890.99
  )
  val invalidIdTypeZeroLength: JsObject = Json.obj(
    "ukChargeableEntityName" -> "New Company",
    "idType"                 -> "",
    "idValue"                -> "abc45678",
    "amountOwedDTT"          -> 12345678901.9,
    "amountOwedIIR"          -> 1234567890.01,
    "amountOwedUTPR"         -> 1234567890.99
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
              "amountOwedDTT"  -> 12345678901.0,
              "amountOwedIIR"  -> 1234567890.09,
              "amountOwedUTPR" -> 6000.50
            )
          )
        )
      )
  )
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
              "amountOwedDTT"          -> 12345678901.0,
              "amountOwedIIR"          -> 1234567890.09,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedDTT"          -> 12345678901.0,
              "amountOwedIIR"          -> 1234567890.09,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedIIR"          -> 1234567890.09,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedDTT"          -> 12345678901.0,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedIIR"          -> 1234567890.09,
              "amountOwedDTT"          -> 12345678901.0
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
              "amountOwedDTT"  -> 12345678901.0,
              "amountOwedIIR"  -> 1234567890.09,
              "amountOwedUTPR" -> 6000.50
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
              "amountOwedDTT"          -> 0.01,
              "amountOwedIIR"          -> .57,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedDTT"          -> 0.01,
              "amountOwedIIR"          -> .57,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedDTT"          -> 0.01,
              "amountOwedIIR"          -> .57,
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedDTT"          -> 0.00,
              "amountOwedIIR"          -> 0,
              "amountOwedUTPR"         -> 0.0
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
              "amountOwedDTT"          -> 0.01,
              "amountOwedIIR"          -> .57,
              "amountOwedUTPR"         -> 6000.50
            ),
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc4567890123456",
              "amountOwedDTT"          -> 0.00,
              "amountOwedIIR"          -> 0,
              "amountOwedUTPR"         -> 0.0
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
              "amountOwedDTT"          -> 50.00,
              "amountOwedIIR"          -> 123456789101112.01,
              "amountOwedUTPR"         -> 12345678901.9
            ),
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc45678",
              "amountOwedDTT"          -> 50.00,
              "amountOwedIIR"          -> 1234567890123.01,
              "amountOwedUTPR"         -> 12345678901.9
            ),
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc45678",
              "amountOwedDTT"          -> 50.00,
              "amountOwedIIR"          -> 123.01,
              "amountOwedUTPR"         -> -123.90
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

  "when submitting a Liability UKTR" - {
    "should return CREATED (201)" - {
      "when plrReference is valid and JSON payload is correct" in {
        val result = route(app, request(body = validRequestBody)).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
      }

      "when plrReference is valid and JSON is correct and has 3 Liable Entities" in {
        val result = route(
          app,
          request(body =
            validRequestBody.deepMerge(
              Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity, validLiableEntity, validLiableEntity)))
            )
          )
        ).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
      }
    }

    "should return UNPROCESSABLE_ENTITY (422)" - {
      "when plrReference indicates business validation failure" in {
        val result = route(app, request(plrReference = "XEPLR0000000422", body = validRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REGIME_MISSING_OR_INVALID_001
        (json \ "errors" \ "text").as[String] mustBe "REGIME missing or invalid"
      }

      "when totalLiability is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiability" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_096
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiability must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityDTT is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityDTT" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_DTT_098
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityDTT must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityIIR is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityIIR" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_IIR_097
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityUTPR is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityUTPR" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_UTPR_099
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityUTPR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when ukChargeableEntityName is Invalid" in {
        val result = route(app, request(body = invalidUkChargeableEntityNameRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when ukChargeableEntityName exceeds 160 characters" in {
        val result = route(app, request(body = ukChargeableEntityNameTooLongRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when ukChargeableEntityName is Empty in 3rd LiableEntity" in {
        val result = route(
          app,
          request(body =
            validRequestBody.deepMerge(
              Json.obj(
                "liabilities" -> Json.obj(
                  "liableEntities" -> Json.arr(validLiableEntity, validLiableEntity, validLiableEntity ++ Json.obj("ukChargeableEntityName" -> ""))
                )
              )
            )
          )
        ).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when idType has zero length" in {
        val result = route(app, request(body = invalidIdTypeZeroLengthRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idType is Invalid" in {
        val result = route(app, request(body = invalidIdTypeRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idType in LiableEntity1 is Invalid and idValue in LiableEntity2 is Invalid" in {
        val result = route(app, request(body = invalidIdTypeEntity1AndInvalidIdValueEntity2RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idValue has zero length" in {
        val result = route(app, request(body = invalidIdValueZeroLengthRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
      }

      "when idValue length exceeds 15 characters" in {
        val result = route(app, request(body = invalidIdValueLengthExceeds15RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
      }

      "when amountOwedDTT is Invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedDTT" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (json \ "errors" \ "text")
            .as[String] mustBe "amountOwedDTT must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedIIR is Invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedIIR" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (json \ "errors" \ "text")
            .as[String] mustBe "amountOwedIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedUTPR is Invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedUTPR" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (json \ "errors" \ "text")
            .as[String] mustBe "amountOwedUTPR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedIIR is Invalid in LiableEntity2 and amountOwedUTPR is Invalid in LiableEntity3" in {
        val result = route(app, request(body = invalidAmountOwedIIREntity2AndInvalidAmountOwedUTPREntity3RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text")
          .as[String] mustBe "amountOwedIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
      }
    }

    "should return BAD_REQUEST (400)" - {
      "when plrReference indicates invalid JSON" in {
        val invalidJson = Json.obj("invalid" -> "json")
        val result      = route(app, request(plrReference = "XEPLR0000000400", body = invalidJson)).value

        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "Invalid message content.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      "when ukChargeableEntityName is missing" in {
        val result = route(app, request(body = missingUkChargeableEntityNameRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when ukChargeableEntityName is missing and invalidLiableEntityukChargeableEntityNameZeroLength" in {
        val result = route(app, request(body = missingUkChargeableEntNameLiableEntity2AndInvalidIdTypeLiableEnt3ReqBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when idType is missing" in {
        val result = route(app, request(body = missingIdTypeRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when idValue is missing" in {
        val result = route(app, request(body = missingIdValueRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedDTT is missing" in {
        val result = route(app, request(body = missingAmountOwedDTTRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedIIR is missing" in {
        val result = route(app, request(body = missingAmountOwedIIRRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when amountOwedUTPR is missing" in {
        val result = route(app, request(body = missingAmountOwedUTPRRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
    }

    "should return INTERNAL_SERVER_ERROR (500)" - {
      "when plrReference indicates SAP failure" in {
        val result = route(app, request(plrReference = "XEPLR0000000500", body = validRequestBody)).value

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code" -> "500",
            "message" -> "Error while sending message to module processor: System Error Received. HTTP Status Code = 200; ErrorCode = INCORRECT_PAYLOAD_DATA; Additional text = Error while processing message payload",
            "logID" -> "C0000AB8190C8E1F000000C700006836"
          )
        )
      }
    }
  }

  "when submitting a nil UKTR" - {
    "should return CREATED (201)" - {
      "when submitting a Domestic-Only Nil Return with electionUKGAAP = true" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a Domestic-Only Nil Return with electionUKGAAP = false" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a Non-Domestic Nil Return with electionUKGAAP = false" in {
        val result =
          route(app, request(plrReference = nonDomesticOnlyPlrReference, body = nilReturnBody(obligationMTT = true, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a domestic-only Nil Return with obligationMTT = false" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a non-domestic Nil Return with obligationMTT = false" in {
        val result =
          route(app, request(plrReference = nonDomesticOnlyPlrReference, body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a non-domestic Nil Return with obligationMTT = true" in {
        val result =
          route(app, request(plrReference = nonDomesticOnlyPlrReference, body = nilReturnBody(obligationMTT = true, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }
    }

    "should return UNPROCESSABLE_ENTITY (422)" - {
      "when submitting a domestic-only Nil Return with obligationMTT = true" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = true, electionUKGAAP = true))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "obligationMTT cannot be true for a domestic-only group or false for a non-domestic group"
      }

      "when submitting a Non-Domestic Nil Return with electionUKGAAP = true" in {
        val result =
          route(app, request(plrReference = nonDomesticOnlyPlrReference, body = nilReturnBody(obligationMTT = true, electionUKGAAP = true))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "electionUKGAAP can be true only for a domestic-only group"
      }
    }

    "should return BAD_REQUEST (400)" - {
      "when NilReturn AccountingPeriodFrom date is invalid" in {
        val result = route(app, request(body = invalidAccountingPeriodFromNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodFrom date is missing" in {
        val result = route(app, request(body = missingAccountingPeriodFromNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodTo date is invalid" in {
        val result = route(app, request(body = invalidAccountingPeriodToNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodTo date is missing" in {
        val result = route(app, request(body = missingAccountingPeriodToNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ObligationMTT field is invalid" in {
        val result = route(app, request(body = invalidObligationMTTNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ObligationMTT field is missing" in {
        val result = route(app, request(body = missingObligationMTTNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ElectionUKGAAP field is invalid" in {
        val result = route(app, request(body = invalidElectionUKGAAPNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ElectionUKGAAP field is missing" in {
        val result = route(app, request(body = missingElectionUKGAAPNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn returnType is invalid" in {
        val result = route(app, request(body = invalidReturnTypeNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn returnType is empty" in {
        val result = route(app, request(body = emptyReturnTypeNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
    }
  }
}
