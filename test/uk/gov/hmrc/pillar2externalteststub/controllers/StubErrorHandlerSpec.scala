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
import uk.gov.hmrc.pillar2externalteststub.models.error._

class StubErrorHandlerSpec extends AnyWordSpec with Matchers {

  private val errorHandler = new StubErrorHandler
  private val dummyRequest = FakeRequest()

  "StubErrorHandler" should {
    "handle client errors" in {
      val result = errorHandler.onClientError(dummyRequest, BAD_REQUEST, "Bad Request Message")
      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "400"
      (json \ "error" \ "message").as[String] shouldBe "Bad request"
    }

    "handle client errors with UNAUTHORIZED status" in {
      val result = errorHandler.onClientError(dummyRequest, UNAUTHORIZED, "Unauthorized Message")
      status(result) shouldBe UNAUTHORIZED
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "401"
      (json \ "error" \ "message").as[String] shouldBe "Unauthorized"
    }

    "handle client errors with FORBIDDEN status" in {
      val result = errorHandler.onClientError(dummyRequest, FORBIDDEN, "Forbidden Message")
      status(result) shouldBe FORBIDDEN
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "403"
      (json \ "error" \ "message").as[String] shouldBe "Forbidden"
    }

    "handle client errors with NOT_FOUND status" in {
      val result = errorHandler.onClientError(dummyRequest, NOT_FOUND, "Not Found Message")
      status(result) shouldBe NOT_FOUND
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "404"
      (json \ "error" \ "message").as[String] shouldBe "Not found"
    }

    "handle client errors with UNPROCESSABLE_ENTITY status" in {
      val result = errorHandler.onClientError(dummyRequest, UNPROCESSABLE_ENTITY, "Validation Failed Message")
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String] shouldBe "Validation Failed Message"
    }

    "handle client errors with other status codes" in {
      val result = errorHandler.onClientError(dummyRequest, PAYMENT_REQUIRED, "Payment Required Message")
      status(result) shouldBe PAYMENT_REQUIRED
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "402"
      (json \ "error" \ "message").as[String] shouldBe "Payment Required Message"
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

    "handle InvalidAccountingPeriod error" in {
      val error = InvalidAccountingPeriod(
        submittedStart = "2024-01-01",
        submittedEnd = "2024-12-31",
        registeredStart = "2023-01-01",
        registeredEnd = "2023-12-31"
      )
      val result = errorHandler.onServerError(dummyRequest, error)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String]   should include("Accounting period")
      (json \ "errors" \ "text").as[String]   should include("does not match the registered period")
    }

    "handle InvalidPillar2Id error" in {
      val error  = InvalidPillar2Id(Some("invalid-id"))
      val result = errorHandler.onServerError(dummyRequest, error)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "002"
      (json \ "errors" \ "text").as[String] shouldBe "PLR Reference is missing or invalid"
    }

    "handle InvalidPillar2Id error with None" in {
      val error  = InvalidPillar2Id(None)
      val result = errorHandler.onServerError(dummyRequest, error)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "002"
      (json \ "errors" \ "text").as[String] shouldBe "PLR Reference is missing or invalid"
    }

    "handle DuplicateSubmissionError" in {
      val error  = DuplicateSubmissionError("XEPLR0000000001")
      val result = errorHandler.onServerError(dummyRequest, error)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "044"
      (json \ "errors" \ "text").as[String] shouldBe "A submission already exists for this accounting period"
    }

    "handle SubmissionNotFoundError" in {
      val error  = SubmissionNotFoundError("XEPLR0000000001")
      val result = errorHandler.onServerError(dummyRequest, error)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String]   should include("No existing submission found to amend for pillar2Id: XEPLR0000000001")
    }

    "handle DomesticOnlyMTTError" in {
      val error  = DomesticOnlyMTTError("XEPLR0000000001")
      val result = errorHandler.onServerError(dummyRequest, error)
      status(result) shouldBe UNPROCESSABLE_ENTITY
      val json = contentAsJson(result)
      (json \ "errors" \ "code").as[String] shouldBe "093"
      (json \ "errors" \ "text").as[String] shouldBe "obligationMTT cannot be true for a domestic-only group"
    }

    "handle unknown errors" in {
      val result = errorHandler.onServerError(dummyRequest, new RuntimeException("Unexpected error"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "error" \ "code").as[String]    shouldBe "500"
      (json \ "error" \ "message").as[String] shouldBe "Internal server error"
    }

    "test consecutive error handling" in {
      // First error - StubError
      val stubResult = errorHandler.onServerError(dummyRequest, DatabaseError("Test database error"))
      status(stubResult) shouldBe INTERNAL_SERVER_ERROR
      val stubJson = contentAsJson(stubResult)
      (stubJson \ "code").as[String] shouldBe "DATABASE_ERROR"

      // Second error - Unknown error
      val unknownResult = errorHandler.onServerError(dummyRequest, new RuntimeException("Test unexpected error"))
      status(unknownResult) shouldBe INTERNAL_SERVER_ERROR
      val unknownJson = contentAsJson(unknownResult)
      (unknownJson \ "error" \ "code").as[String] shouldBe "500"
    }
  }
}
