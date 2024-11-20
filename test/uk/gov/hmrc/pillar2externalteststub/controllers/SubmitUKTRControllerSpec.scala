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

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.Materializer
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.CREATED
import play.api.libs.json.{JsValue, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import play.api.libs.json.JsObject

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
  "SubmitUKTRController POST" - {
    "return CREATED with success response when plrReference is valid and JSON is correct" in {
      println("validRequestBody =" + validRequestBody)

      val authHeader: (String, String) = HeaderNames.authorisation -> "Bearer valid_token"
      val request = FakeRequest(POST, uk.gov.hmrc.pillar2externalteststub.controllers.routes.SubmitUKTRController.submitUKTR("UKTR0123456789").url)
        .withHeaders("Content-Type" -> "application/json", authHeader)
        .withBody(validRequestBody)

      val result = route(app, request).value

      implicit val system:       ActorSystem  = ActorSystem("MySystem")
      implicit val materializer: Materializer = Materializer(system)
      result.map { result =>
        val jsonBodyString = result.body.consumeData.map(_.decodeString("UTF-8"))
        jsonBodyString.foreach { body =>
          val json: JsValue = Json.parse(body)
          val prettyJson = Json.prettyPrint(json)
          println("zxc completedResult from response prettyJson=" + prettyJson)
        }
      }

      status(result) mustBe CREATED
      val json           = contentAsJson(result)
      val processingDate = (json \ "processingDate").asOpt[String]
      processingDate.isDefined shouldBe true
      //processingDate.exists(isValidDateTimeFormat) shouldBe true
      (json \ "formBundleNumber").as[String] mustBe "123456789123"
      (json \ "chargeReference").as[String] mustBe "XTC01234123412"
    }
  }

  def isValidDateTimeFormat(dateTimeString: String): Boolean = {
    val formatter = DateTimeFormatter.ofPattern(dateTimePattern).withZone(java.time.ZoneOffset.UTC)
    try {
      ZonedDateTime.parse(dateTimeString, formatter)
      true
    } catch {
      case _: Exception => false
    }
  }
}
