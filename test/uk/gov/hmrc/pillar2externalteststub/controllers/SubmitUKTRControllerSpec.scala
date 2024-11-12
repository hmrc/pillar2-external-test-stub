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
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.CREATED
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames

import scala.concurrent.Await

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues {

  "SubmitUKTRController POST" - {
    "return CREATED with success response when plrReference is valid and JSON is correct" in {
      val validRequestBody = Json.obj(
        "accountingPeriodFrom" -> "2024-08-14",
        "accountingPeriodTo"   -> "2024-12-14",
        "qualifyingGroup"      -> true,
        "obligationDTT"        -> true,
        "obligationMTT"        -> true,
        "liabilities" -> Json.obj(
          //"electionDTTSingleMember" ->  true,
          //"electionUTPRSingleMember" ->  true,
          //"numberSubGroupDTT" ->  123,
          //"numberSubGroupUTPR" ->  456,
          "totalLiability"     -> 10000.99,
          "totalLiabilityDTT"  -> 5000.99,
          "totalLiabilityIIR"  -> 4000,
          "totalLiabilityUTPR" -> 10000.99,
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
      println("validRequestBody =" + validRequestBody)

      val authHeader: (String, String) = HeaderNames.authorisation -> "Bearer valid_token"
      val request = FakeRequest(POST, uk.gov.hmrc.pillar2externalteststub.controllers.routes.SubmitUKTRController.submitUKTR("UKTR0123456789").url)
        .withHeaders("Content-Type" -> "application/json", authHeader)
        .withBody(validRequestBody)

      val result = route(app, request).value
      val completedResult: Result = Await.result(result, 1.seconds)
      println("zxc completedResult from response =" + completedResult + ".")

      //val currentDate = LocalDate.now().toString
      status(result) mustBe CREATED

//      contentAsJson(result) mustBe Json.parse(
//        s"""{"success":{"processingDate":"${currentDate}T09:26:17Z"}}"""
//      )

    }
  }
  //}
}
