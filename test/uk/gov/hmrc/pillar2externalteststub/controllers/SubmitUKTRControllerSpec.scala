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

  val validRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "qualifyingGroup"      -> true,
    "obligationDTT"        -> true,
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
          "electedDTT"             -> true,
          "amountOwedIIR"          -> 3400,
          "amountOwedUTPR"         -> 6000.5,
          "electedUTPR"            -> true
        )
      )
    )
  )

  //  =============================================================
  //  BAD REQUEST 400 JSON ERRORS - MISSING LIABILITY ENTITY FIELDS
  //  =============================================================
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

  //  =============================================================
  //  invalid UKTR REQUESTS :  422 Business Validation Failures
  //  =============================================================

  val invalidUkChargeableEntityNameRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "",
              "idType"                 -> "CRN",
              "idValue"                -> "12345678",
              "amountOwedDTT"          -> 12345678901.0, // valid amt: 13 digits max, including the decimal point
              "electedDTT"             -> true,
              "amountOwedIIR"          -> 1234567890.09, // valid amt: 13 digits max, including the decimal point
              "amountOwedUTPR"         -> 6000.50,
              "electedUTPR"            -> true
            )
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
              "amountOwedDTT"  -> 12345678901.0, // valid amt: 13 digits max, including the decimal point
              "amountOwedIIR"  -> 1234567890.09, // valid amt: 13 digits max, including the decimal point
              "amountOwedUTPR" -> 6000.50
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
              "amountOwedDTT"          -> 0.01, // valid amt
              "amountOwedIIR"          -> .57, // valid amt
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
              "idValue"                -> "", // length=0 => invalid: min is 1.
              "amountOwedDTT"          -> 0.01, // valid amt
              "amountOwedIIR"          -> .57, // valid amt
              "amountOwedUTPR"         -> 6000.50
            )
          )
        )
      )
  )

  val invalidIdValueLengthRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "UTR",
              "idValue"                -> "abc4567890123456", // length=16 => invalid: max is 15.
              "amountOwedDTT"          -> 0.01, // valid amt
              "amountOwedIIR"          -> .57, // valid amt
              "amountOwedUTPR"         -> 6000.50
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
              "amountOwedDTT"          -> -50.00, // invalid negative value
              "amountOwedIIR"          -> 0.01,
              "amountOwedUTPR"         -> 12345678901.9 // valid amt: 13 digits max, including the decimal point
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
              "amountOwedIIR"          -> 1234567890123.01, // invalid amt: > 13 digits, including the decimal point
              "amountOwedUTPR"         -> 12345678901.9
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
              "amountOwedIIR"          -> 1234567890.01, // valid amt:  13 digits, including the decimal point
              "amountOwedUTPR"         -> 1234567890123.1 // invalid amt: > 13 digits, including the decimal point
            )
          )
        )
      )
  )

  val validNilReturnRequestBody: JsObject = Json.obj(
    "accountingPeriodFrom" -> "2024-08-14",
    "accountingPeriodTo"   -> "2024-12-14",
    "qualifyingGroup"      -> true,
    "obligationDTT"        -> true,
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
              "message" -> "Invalid JSON message content used; Message: \"Expected a ',' or '}' at character 93...\"",
              "logID"   -> "C0000AB8190C86300000000200006836"
            )
          )
        }
      }

      //  =============================================================
      //  BAD REQUEST 400 JSON ERRORS - MISSING LIABILITY ENTITY FIELDS
      //  =============================================================
      "when ukChargeableEntityName is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingUkChargeableEntityNameRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "ukChargeableEntityName is a mandatory field in each LiabilityEntity object.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      "when idType is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingIdTypeRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "idType is a mandatory field in each LiabilityEntity object.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      "when idValue is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingIdValueRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "idValue is a mandatory field in each LiabilityEntity object.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      "when amountOwedDTT is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingAmountOwedDTTRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "amountOwedDTT is a mandatory field in each LiabilityEntity object.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      "when amountOwedIIR is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingAmountOwedIIRRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "amountOwedIIR is a mandatory field in each LiabilityEntity object.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      "when amountOwedUTPR is missing" in {
        val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
          .withHeaders("Content-Type" -> "application/json", authHeader)
          .withBody(missingAmountOwedUTPRRequestBody)
        val result = route(app, request).value
        status(result) mustBe BAD_REQUEST
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "400",
            "message" -> "amountOwedUTPR is a mandatory field in each LiabilityEntity object.",
            "logID"   -> "C0000AB8190C86300000000200006836"
          )
        )
      }

      // =============================================
      // 422 business validation failure errors
      // =============================================
      "should return UNPROCESSABLE_ENTITY (422) + business validation failure error code 003" - {
        "when ukChargeableEntityName is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidUkChargeableEntityNameRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text"           -> "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
            )
          )
        }

        "when ukChargeableEntityName exceeds 160 characters" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(ukChargeableEntityNameTooLongRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text"           -> "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
            )
          )
        }

        "when idType is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdTypeRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text"           -> "idType must be either UTR or CRN."
            )
          )
        }

        "when idValue has zero length" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdValueZeroLengthRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text"           -> "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
            )
          )
        }

        "when idValue length exceeds 15 characters" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdValueLengthRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text"           -> "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
            )
          )
        }

        "when amountOwedDTT is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedDTTRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text" -> "amountOwedDTT must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
            )
          )
        }

        "when amountOwedIIR is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedIIRRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text" -> "amountOwedIIR must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
            )
          )
        }

        "when amountOwedUTPR is Invalid" in {
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedUTPRRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.obj(
              "processingDate" -> UktrSubmission.UKTR_STUB_PROCESSING_DATE,
              "code"           -> UktrErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              "text" -> "amountOwedUTPR must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
            )
          )
        }
      }
    }
  }
}
