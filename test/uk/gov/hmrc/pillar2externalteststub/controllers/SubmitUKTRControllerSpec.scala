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

import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.CREATED
import play.api.libs.json.JsObject
import play.api.libs.json.Json
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UktrSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.error.UktrErrorCodes

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues {
  val authHeader: (String, String) = HeaderNames.authorisation -> "Bearer valid_token"

  def create400JSONErrorBody(fieldName: String, errorMessage: String): JsObject =
    Json.obj(
      "error" -> Json.obj(
        "code"    -> "400",
        "message" -> s"$fieldName $errorMessage",
        "logID"   -> "C0000AB8190C86300000000200006836"
      )
    )

  val ukChargeableEntityName422JSONError: JsObject = Json.obj(
    "error" -> Json.obj(
      "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
      "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
      "text"           -> "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
    )
  )
  val idType422JSONError: JsObject = Json.obj(
    "error" -> Json.obj(
      "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
      "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
      "text"           -> "idType must be either UTR or CRN."
    )
  )
  val idValue422JSONError: JsObject = Json.obj(
    "error" -> Json.obj(
      "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
      "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
      "text"           -> "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
    )
  )
  val amountOwedDTT422JSONError: JsObject = Json.obj(
    "error" -> Json.obj(
      "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
      "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
      "text" -> "amountOwedDTT must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
    )
  )
  val amountOwedIIR422JSONError: JsObject = Json.obj(
    "error" -> Json.obj(
      "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
      "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
      "text" -> "amountOwedIIR must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
    )
  )
  val amountOwedUTPR422JSONError: JsObject = Json.obj(
    "error" -> Json.obj(
      "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
      "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
      "text" -> "amountOwedUTPR must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
    )
  )

  val validRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> true,
    "liabilities" -> Json.obj(
      "electionDTTSingleMember"  -> false,
      "electionUTPRSingleMember" -> false,
      "numberSubGroupDTT"        -> 4,
      "numberSubGroupUTPR"       -> 5,
      "totalLiability"           -> 10000.99,
      "totalLiabilityDTT"        -> 5000.99,
      "totalLiabilityIIR"        -> 4000,
      "totalLiabilityUTPR"       -> 10000.99,
      "liableEntities" -> Json.arr(
        Json.obj(
          "ukChargeableEntityName" -> "UKTR Newco PLC",
          "idType"                 -> "CRN",
          "idValue"                -> "12345678",
          "amountOwedDTT"          -> 5000,
          "amountOwedIIR"          -> 3400,
          "amountOwedUTPR"         -> 6000.5
        )
      )
    )
  )
  val validLiableEntity1: JsObject = Json.obj(
    "ukChargeableEntityName" -> "New Company",
    "idType"                 -> "CRN",
    "idValue"                -> "1234",
    "amountOwedDTT"          -> 12345678901.0,
    "amountOwedIIR"          -> 1234567890.09,
    "amountOwedUTPR"         -> 600.50
  )
  val validLiableEntity2: JsObject = Json.obj(
    "ukChargeableEntityName" -> "UK Co",
    "idType"                 -> "UTR",
    "idValue"                -> "a",
    "amountOwedDTT"          -> 10.00,
    "amountOwedIIR"          -> 10,
    "amountOwedUTPR"         -> 10.0
  )
  val validLiableEntity3: JsObject = Json.obj(
    "ukChargeableEntityName" -> "UK Company",
    "idType"                 -> "UTR",
    "idValue"                -> "abc45678",
    "amountOwedDTT"          -> 12345678901.9,
    "amountOwedIIR"          -> 1234567890.01,
    "amountOwedUTPR"         -> 1234567890.99
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

  val validThreeLiableEntitiesRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            validLiableEntity1,
            validLiableEntity2,
            validLiableEntity3
          )
        )
      )
  )

  val invalidUkChargeableEntityNameThirdLiableEntityRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            validLiableEntity1,
            validLiableEntity2,
            invalidLiableEntityukChargeableEntityNameZeroLength
          )
        )
      )
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
            validLiableEntity1,
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
            Json.obj( // ukChargeableEntityName is 161 chars long:
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
              "amountOwedDTT"          -> 0.01, // valid amt
              "amountOwedIIR"          -> .57, // valid amt
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

  val invalidAmountOwedDTTRequestBody: JsObject = validRequestBody ++ Json.obj(
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
              "amountOwedDTT"          -> -50.00,
              "amountOwedIIR"          -> 0.01,
              "amountOwedUTPR"         -> 12345678901.9
            )
          )
        )
      )
  )

  val invalidAmountOwedIIRRequestBody: JsObject = validRequestBody ++ Json.obj(
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
              "amountOwedIIR"          -> 1234567890123.01,
              "amountOwedUTPR"         -> 12345678901.9
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
              "amountOwedIIR"          -> 123.01,
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

  val invalidAmountOwedUTPRRequestBody: JsObject = validRequestBody ++ Json.obj(
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
              "amountOwedIIR"          -> 1234567890.01,
              "amountOwedUTPR"         -> 1234567890123.1
            )
          )
        )
      )
  )

  val validNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "obligationMTT"        -> true,
    "electionUKGAAP"       -> true,
    "liabilities" -> Json.obj(
      "returnType" -> "NIL_RETURN"
    )
  )

  "SubmitUKTRController" - {
    "when submitting UKTR" - {
      "should return CREATED (201) with success response" - {
        "when plrReference is valid and JSON payload is correct" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000123").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validRequestBody)
          val result = route(app, request).value
          status(result) mustBe CREATED
          val json = contentAsJson(result)
          (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
          (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
          (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        }

        "when plrReference is valid and JSON is correct and has 3 Liable Entities" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000123").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validThreeLiableEntitiesRequestBody)
          val result = route(app, request).value
          status(result) mustBe CREATED
          val json = contentAsJson(result)
          (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
          (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
          (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        }

        "when submitting a nil return" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000002").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validNilReturnRequestBody)
          val result = route(app, request).value
          status(result) mustBe CREATED
          val json = contentAsJson(result)
          (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
          (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
          (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        }
      }

      "should return UNPROCESSABLE_ENTITY (422)" - {
        "when plrReference indicates business validation failure" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000422").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "errors" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> "001",
              "text"           -> "REGIME missing or invalid"
            )
          )
        }
      }

      "should return INTERNAL_SERVER_ERROR (500)" - {
        "when plrReference indicates SAP failure" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000500").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validRequestBody)
          val result = route(app, request).value
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

      "should return BAD_REQUEST (400)" - {
        "when plrReference indicates invalid JSON" in {
          val invalidJson = Json.obj("invalid" -> "json")
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000400").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidJson)
          val result = route(app, request).value
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "code"    -> "400",
              "message" -> "Invalid message content.",
              "logID"   -> "C0000AB8190C86300000000200006836"
            )
          )
        }
      }

      "when ukChargeableEntityName is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingUkChargeableEntityNameRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "when ukChargeableEntityName is missing and invalidLiableEntityukChargeableEntityNameZeroLength" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingUkChargeableEntNameLiableEntity2AndInvalidIdTypeLiableEnt3ReqBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "when idType is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingIdTypeRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "when idValue is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingIdValueRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedDTT is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingAmountOwedDTTRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedIIR is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingAmountOwedIIRRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedUTPR is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingAmountOwedUTPRRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
      }

      "should return UNPROCESSABLE_ENTITY (422) + business validation failure error code 003" - {
        "when ukChargeableEntityName is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidUkChargeableEntityNameRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe ukChargeableEntityName422JSONError
        }

        "when ukChargeableEntityName exceeds 160 characters" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(ukChargeableEntityNameTooLongRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe ukChargeableEntityName422JSONError
        }

        "when ukChargeableEntityName is Empty in 3rd LiableEntity" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidUkChargeableEntityNameThirdLiableEntityRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe ukChargeableEntityName422JSONError
        }

        "when idType has zero length" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdTypeZeroLengthRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe idType422JSONError
        }

        "when idType is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdTypeRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe idType422JSONError
        }

        "when idType in LiableEntity1 is Invalid and idValue in LiableEntity2 is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdTypeEntity1AndInvalidIdValueEntity2RequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe idType422JSONError
        }

        "when idValue has zero length" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdValueZeroLengthRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe idValue422JSONError
        }

        "when idValue length exceeds 15 characters" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdValueLengthExceeds15RequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe idValue422JSONError
        }

        "when amountOwedDTT is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedDTTRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe amountOwedDTT422JSONError
        }

        "when amountOwedIIR is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedIIRRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe amountOwedIIR422JSONError
        }

        "when amountOwedUTPR is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedUTPRRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe amountOwedUTPR422JSONError
        }

        "when amountOwedIIR is Invalid in LiableEntity2 and amountOwedUTPR is Invalid in LiableEntity3" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedIIREntity2AndInvalidAmountOwedUTPREntity3RequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe amountOwedIIR422JSONError
        }
      }
    }
  }
}
