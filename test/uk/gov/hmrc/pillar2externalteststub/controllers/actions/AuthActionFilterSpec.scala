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

package uk.gov.hmrc.pillar2externalteststub.controllers.actions

import org.scalatest.Assertion
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.Results
import play.api.test.FakeRequest
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2DataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest

import scala.concurrent.ExecutionContext.Implicits.global

class AuthActionFilterSpec extends AnyWordSpec with Matchers with ScalaFutures with Pillar2DataFixture {

  val authActionFilter = new AuthActionFilter()

  "AuthActionFilter" should {
    "allow request with valid headers" in {
      val request = FakeRequest().withHeaders(hipHeaders: _*)
      authActionFilter.filter(request).futureValue shouldBe None
    }

    "return Unauthorized when Authorization header is missing" in {
      val request = FakeRequest().withHeaders(hipHeaders.tail: _*)
      authActionFilter.filter(request).futureValue shouldBe Some(Results.Unauthorized)
    }

    "throw ETMPBadRequest" when {
      def testHeader(header: String, invalidValue: String = ""): Assertion = {
        val headers = {
          val filteredHeaders = hipHeaders.filterNot(_._1 == header)
          if (invalidValue.isEmpty) filteredHeaders else filteredHeaders :+ (header -> invalidValue)
        }
        authActionFilter.filter(FakeRequest().withHeaders(headers: _*)) shouldFailWith HIPBadRequest(s"Header is missing or invalid: $header")
      }

      "correlationid is missing" in {
        testHeader(correlationidHeader)
      }

      "correlationid is invalid" in {
        testHeader(correlationidHeader, "invalid-correlation-id")
      }

      "X-Receipt-Date header is missing" in {
        testHeader(xReceiptDateHeader)
      }

      "X-Receipt-Date header is invalid" in {
        testHeader(xReceiptDateHeader, "invalid-date")
      }

      "X-Originating-System header is missing" in {
        testHeader(xOriginatingSystemHeader)
      }

      "X-Originating-System header is invalid" in {
        testHeader(xOriginatingSystemHeader, "INVALID")
      }

      "X-Transmitting-System header is missing" in {
        testHeader(xTransmittingSystemHeader)
      }

      "X-Transmitting-System header is invalid" in {
        testHeader(xTransmittingSystemHeader, "INVALID")
      }
    }
  }
}
