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

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues {
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
              "amountOwedDTT"          -> 12345678901.0,  // valid amt: 13 digits max, including the decimal point
              "electedDTT"             -> true,
              "amountOwedIIR"          -> 1234567890.09, // valid amt: 13 digits max, including the decimal point
              "amountOwedUTPR"         -> 6000.50,
              "electedUTPR"            -> true
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
              "amountOwedDTT"          -> 0.01,            // valid amt
              "electedDTT"             -> true,
              "amountOwedIIR"          -> .57,             // valid amt
              "amountOwedUTPR"         -> 6000.50,
              "electedUTPR"            -> true
            )
          )
        )
      )
  )

  val invalidIidValueLengthRequestBody: JsObject = validRequestBody ++ Json.obj(
    "liabilities" -> validRequestBody
      .value("liabilities")
      .as[JsObject]
      .deepMerge(
        Json.obj(
          "liableEntities" -> Json.arr(
            Json.obj(
              "ukChargeableEntityName" -> "UK Company",
              "idType"                 -> "CT-UTR",
              "idValue"                -> "abc4567890123456",      // length=16 => invalid: max is 15.
              "amountOwedDTT"          -> 0.01,            // valid amt
              "electedDTT"             -> true,
              "amountOwedIIR"          -> .57,             // valid amt
              "amountOwedUTPR"         -> 6000.50,
              "electedUTPR"            -> true
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
              "idType"                 -> "CT-UTR",
              "idValue"                -> "abc45678",
              "amountOwedDTT"          -> -50.00,          // invalid negative value
              "electedDTT"             -> true,
              "amountOwedIIR"          -> 0.01,
              "amountOwedUTPR"         -> 12345678901.9,  // valid amt: 13 digits max, including the decimal point
              "electedUTPR"            -> true
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
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
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
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
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
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000422").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validRequestBody)
          val result = route(app, request).value
          status(result) mustBe UNPROCESSABLE_ENTITY
          contentAsJson(result) mustBe Json.obj(
            "errors" -> Json.obj(
              "processingDate" -> "2022-01-31T09:26:17Z",
              "code"           -> "001",
              "text"           -> "REGIME missing or invalid"
            )
          )
        }
      }

      "should return INTERNAL_SERVER_ERROR (500)" - {
        "when plrReference indicates SAP failure" in {
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
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
          val authHeader  = HeaderNames.authorisation -> "Bearer valid_token"
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

      "should return BAD_REQUEST (400) + error code 003" - {
        "when ukChargeableEntityName is Invalid" in {
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidUkChargeableEntityNameRequestBody)
          val result = route(app, request).value
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.arr(
              Json.obj(
                "code"    -> "003",
                "field"   -> "ukChargeableEntityName",
                "message" -> "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
              )
            )
          )
        }

        "when idType is Invalid" in {
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIdTypeRequestBody)
          val result = route(app, request).value
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.arr(
              Json.obj(
                "code"    -> "003",
                "field"   -> "idType",
                "message" -> "idType must be either CT-UTR or CRN."
              )
            )
          )
        }

        "when idValue length is Invalid" in {
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidIidValueLengthRequestBody)
          val result = route(app, request).value
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.arr(
              Json.obj(
                "code"    -> "003",
                "field"   -> "idValue",
                "message" -> "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
              )
            )
          )
        }

        "when amountOwedDTT is Invalid" in {
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("XEPLR0000000003").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidAmountOwedDTTRequestBody)
          val result = route(app, request).value
          status(result) mustBe BAD_REQUEST
          contentAsJson(result) mustBe Json.obj(
            "error" -> Json.arr(
              Json.obj(
                "code"  -> "003",
                "field" -> "amountOwedDTT",
                "message" -> "amountOwedDTT must be Numeric, positive, with at most 2 decimal places, and less than or equal to 13 characters, including the decimal place."
              )
            )
          )
        }
      }
    }
  }
}
