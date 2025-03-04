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

import org.apache.pekko.util.Timeout
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.http.Status._
import play.api.libs.json.{JsValue, Json}
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers.{contentAsJson, status}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.{TestOrganisationWithId, UKTRSubmission}
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.tools.Stubs.stubMessagesControllerComponents

import java.time.LocalDate
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import play.api.http.HeaderNames

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with MockitoSugar with BeforeAndAfterEach {

  implicit val timeout: Timeout          = Timeout(5.seconds)
  implicit val ec:      ExecutionContext = ExecutionContext.global

  val mockUKTRSubmissionRepository: UKTRSubmissionRepository = mock[UKTRSubmissionRepository]
  val mockOrganisationService:      OrganisationService      = mock[OrganisationService]
  val authActionFilter = new AuthActionFilter(stubMessagesControllerComponents())

  val controller = new SubmitUKTRController(
    stubMessagesControllerComponents(),
    mockUKTRSubmissionRepository,
    mockOrganisationService,
    authActionFilter
  )

  val authHeader: (String, String) = HeaderNames.AUTHORIZATION -> "Bearer token"
  val plrReference = "XMPLR0123456789"
  val validRequestBody: JsValue = Json.parse(
    s"""
       |{
       |  "accountingPeriod": {
       |    "startDate": "2022-04-01",
       |    "endDate": "2023-03-31"
       |  }
       |}
       |""".stripMargin
  )

  val testOrganisation = TestOrganisationWithId(
    plrReference,
    "Test Org",
    "12345",
    "GB",
    None,
    None,
    None,
    None,
    None,
    None,
    None
  )

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockUKTRSubmissionRepository, mockOrganisationService)

    when(mockOrganisationService.getOrganisation(any[String]))
      .thenReturn(Future.successful(Some(testOrganisation)))

    when(mockUKTRSubmissionRepository.insert(any[UKTRSubmission]))
      .thenReturn(Future.successful(true))
  }

  private def createRequest(body: JsValue = validRequestBody): FakeRequest[JsValue] =
    FakeRequest("POST", "/submit-uktr")
      .withHeaders(
        authHeader,
        "X-Pillar2-ID" -> plrReference
      )
      .withBody(body)

  "Submit UK Tax Return" - {
    "should return CREATED when a valid submission is made" in {
      val request = createRequest()

      val result = controller.submitUKTR()(request)

      status(result) shouldBe CREATED
    }

    "should return UNPROCESSABLE_ENTITY when X-Pillar2-ID header is missing" in {
      val request = FakeRequest("POST", "/submit-uktr")
        .withHeaders(authHeader)
        .withBody(validRequestBody)

      val result = controller.submitUKTR()(request)

      status(result)               shouldBe UNPROCESSABLE_ENTITY
      contentAsJson(result).toString should include("Missing or invalid X-Pillar2-ID header")
    }

    "should return UNPROCESSABLE_ENTITY when organization does not exist" in {
      when(mockOrganisationService.getOrganisation(eqTo(plrReference)))
        .thenReturn(Future.successful(None))

      val request = createRequest()

      val result = controller.submitUKTR()(request)

      status(result)               shouldBe UNPROCESSABLE_ENTITY
      contentAsJson(result).toString should include("Organization not found")
    }

    "should return BAD_REQUEST when request body is invalid" in {
      val invalidBody = Json.parse("""{"invalid": "json"}""")
      val request     = createRequest(invalidBody)

      val result = controller.submitUKTR()(request)

      status(result) shouldBe BAD_REQUEST
    }
  }
}
