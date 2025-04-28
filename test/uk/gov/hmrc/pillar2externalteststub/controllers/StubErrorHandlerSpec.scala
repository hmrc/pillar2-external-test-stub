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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error._

class StubErrorHandlerSpec extends AnyWordSpec with Matchers {

  private val errorHandler = new StubErrorHandler
  private val dummyRequest = FakeRequest()

  "StubErrorHandler" should {
    "handle client errors" in {
      val badRequest = errorHandler.onClientError(dummyRequest, BAD_REQUEST)
      val notFound   = errorHandler.onClientError(dummyRequest, NOT_FOUND)

      status(badRequest) shouldBe BAD_REQUEST
      status(notFound)   shouldBe NOT_FOUND
    }

    "handle InvalidJson error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidJson)
      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "INVALID_JSON"
      (json \ "message").as[String] shouldBe "Invalid JSON payload provided"
    }

    "handle EmptyRequestBody error" in {
      val result = errorHandler.onServerError(dummyRequest, EmptyRequestBody)
      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "EMPTY_REQUEST_BODY"
      (json \ "message").as[String] shouldBe "Empty request body provided"
    }

    "handle OrganisationAlreadyExists error" in {
      val result = errorHandler.onServerError(dummyRequest, OrganisationAlreadyExists("TEST123"))
      status(result) shouldBe CONFLICT
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "ORGANISATION_EXISTS"
      (json \ "message").as[String] shouldBe "Organisation with pillar2Id: TEST123 already exists"
    }

    "handle OrganisationNotFound error" in {
      val result = errorHandler.onServerError(dummyRequest, OrganisationNotFound("TEST123"))
      status(result) shouldBe NOT_FOUND
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "ORGANISATION_NOT_FOUND"
      (json \ "message").as[String] shouldBe "No organisation found with pillar2Id: TEST123"
    }

    "handle DatabaseError" in {
      val result = errorHandler.onServerError(dummyRequest, DatabaseError("Connection failed"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "DATABASE_ERROR"
      (json \ "message").as[String] shouldBe "Connection failed"
    }

    "handle RequestCouldNotBeProcessed error" in {
      val result = errorHandler.onServerError(dummyRequest, RequestCouldNotBeProcessed)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"
    }

    "handle NoFormBundleFound error" in {
      val result = errorHandler.onServerError(dummyRequest, NoFormBundleFound)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "005"
      (json \ "errors" \ "text").as[String] shouldBe "No Form Bundle found"
    }

    "handle NoActiveSubscription error" in {
      val result = errorHandler.onServerError(dummyRequest, NoActiveSubscription)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "063"
      (json \ "errors" \ "text").as[String] shouldBe "Business Partner does not have an Active Subscription for this Regime"
    }

    "handle NoDataFound error" in {
      val result = errorHandler.onServerError(dummyRequest, NoDataFound)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "014"
      (json \ "errors" \ "text").as[String] shouldBe "No data found"
    }

    "handle TaxObligationAlreadyFulfilled error" in {
      val result = errorHandler.onServerError(dummyRequest, TaxObligationAlreadyFulfilled)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "044"
      (json \ "errors" \ "text").as[String] shouldBe "Tax obligation already fulfilled"
    }

    "handle IdMissingOrInvalid error" in {
      val result = errorHandler.onServerError(dummyRequest, IdMissingOrInvalid)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "089"
      (json \ "errors" \ "text").as[String] shouldBe "ID number missing or invalid"
    }

    "handle InvalidReturn error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidReturn)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "093"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid Return"
    }

    "handle InvalidDTTElection error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidDTTElection)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "094"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid DTT Election"
    }

    "handle InvalidUTPRElection error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidUTPRElection)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "095"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid UTPR Election"
    }

    "handle InvalidTotalLiability error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidTotalLiability)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "096"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid Total Liability"
    }

    "handle InvalidTotalLiabilityIIR error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidTotalLiabilityIIR)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "097"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid Total Liability IIR"
    }

    "handle InvalidTotalLiabilityDTT error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidTotalLiabilityDTT)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "098"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid Total Liability DTT"
    }

    "handle InvalidTotalLiabilityUTPR error" in {
      val result = errorHandler.onServerError(dummyRequest, InvalidTotalLiabilityUTPR)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "099"
      (json \ "errors" \ "text").as[String] shouldBe "Invalid Total Liability UTPR"
    }

    "handle ETMPBadRequest error" in {
      val result = errorHandler.onServerError(dummyRequest, ETMPBadRequest)
      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "400"
      (json \ "error" \ "message").as[String] shouldBe "Bad request"
      (json \ "error" \ "logID").as[String]   shouldBe "C0000000000000000000000000000400"
    }

    "handle ETMPInternalServerError error" in {
      val result = errorHandler.onServerError(dummyRequest, ETMPInternalServerError)
      status(result) shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "500"
      (json \ "error" \ "message").as[String] shouldBe "Internal server error"
      (json \ "error" \ "logID").as[String]   shouldBe "C0000000000000000000000000000500"
    }

    "handle unknown errors" in {
      val result = errorHandler.onServerError(dummyRequest, new RuntimeException("Unexpected error"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "500"
      (json \ "error" \ "message").as[String] shouldBe "Internal server error"
    }
  }
}
