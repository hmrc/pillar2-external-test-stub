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
      val result = errorHandler.onClientError(dummyRequest, BAD_REQUEST, "Bad Request Message")
      status(result) shouldBe BAD_REQUEST
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "400"
      (json \ "message").as[String] shouldBe "Bad Request Message"
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

    "handle unknown errors" in {
      val result = errorHandler.onServerError(dummyRequest, new RuntimeException("Unexpected error"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      val json = contentAsJson(result)
      (json \ "code").as[String]    shouldBe "500"
      (json \ "message").as[String] shouldBe "Internal Server Error"
    }
  }
}
