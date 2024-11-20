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
import uk.gov.hmrc.pillar2externalteststub.models.uktr._

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues {
  val dateTimePattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'"

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
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("P2ID0000000123").url)
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
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("P2ID0000000123").url)
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
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("P2ID0000000422").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validRequestBody)

          val result = route(app, request).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          (contentAsJson(result) \ "errors").as[UKTRErrorDetail] mustBe ValidationError422.response
        }
      }

      "should return INTERNAL_SERVER_ERROR (500)" - {
        "when plrReference indicates SAP failure" in {
          val authHeader = HeaderNames.authorisation -> "Bearer valid_token"
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("P2ID0000000500").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(validRequestBody)

          val result = route(app, request).value

          status(result) mustBe INTERNAL_SERVER_ERROR
          (contentAsJson(result) \ "error").as[UKTRError] mustBe SAPError500.response
        }
      }

      "should return BAD_REQUEST (400)" - {
        "when request body is invalid JSON" in {
          val authHeader  = HeaderNames.authorisation -> "Bearer valid_token"
          val invalidJson = Json.obj("invalid" -> "json")
          val request = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR("P2ID0000000123").url)
            .withHeaders("Content-Type" -> "application/json", authHeader)
            .withBody(invalidJson)

          val result = route(app, request).value

          status(result) mustBe BAD_REQUEST
          (contentAsJson(result) \ "errors").as[UKTRError] mustBe InvalidJsonError400.response
        }
      }
    }
  }
}
