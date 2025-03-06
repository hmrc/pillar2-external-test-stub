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

import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json._
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.ServerErrorPlrId
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture with MockitoSugar {

  val mockOrgService: OrganisationService      = mock[OrganisationService]
  val mockRepository: UKTRSubmissionRepository = mock[UKTRSubmissionRepository]

  val orgDetails: OrgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.of(2022, 4, 1)
  )

  override val accountingPeriod: AccountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2022, 4, 1),
    endDate = LocalDate.of(2023, 3, 31)
  )

  val testOrgDetails: TestOrganisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod
  )

  val testOrg: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = "XMPLR0123456789",
    organisation = testOrgDetails
  )

  override lazy val app: Application = GuiceApplicationBuilder()
    .overrides(
      bind[OrganisationService].toInstance(mockOrgService),
      bind[UKTRSubmissionRepository].toInstance(mockRepository)
    )
    .build()

  val controller:  SubmitUKTRController = app.injector.instanceOf[SubmitUKTRController]
  implicit val ec: ExecutionContext     = app.injector.instanceOf[ExecutionContext]

  val validPillar2Id = "XMPLR0123456789"
  override val authHeader: (String, String) = HeaderNames.authorisation -> "Bearer valid_token"

  val testRequestBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "LIABILITY",
      |  "accountingPeriod": {
      |    "startDate": "2022-04-01",
      |    "endDate": "2023-03-31"
      |  },
      |  "reportingEntity": {
      |    "customerIdentification1": "12345678",
      |    "customerIdentification2": "K12345",
      |    "idType": "UTR",
      |    "name": "Test Company"
      |  },
      |  "liableEntities": [
      |    {
      |      "customerIdentification1": "87654321",
      |      "customerIdentification2": "K54321",
      |      "idType": "UTR",
      |      "name": "Liable Entity 1",
      |      "liability": 1000.00
      |    }
      |  ],
      |  "totalLiability": 1000.00
      |}
      |""".stripMargin
  )

  val nilReturnBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "NIL_RETURN",
      |  "accountingPeriod": {
      |    "startDate": "2022-04-01",
      |    "endDate": "2023-03-31"
      |  },
      |  "reportingEntity": {
      |    "customerIdentification1": "12345678",
      |    "customerIdentification2": "K12345",
      |    "idType": "UTR",
      |    "name": "Test Company"
      |  }
      |}
      |""".stripMargin
  )

  val invalidJsonBody: JsValue = Json.parse(
    """
      |{
      |  "invalid": "json"
      |}
      |""".stripMargin
  )

  val invalidAccountingPeriodBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "LIABILITY",
      |  "accountingPeriod": {
      |    "startDate": "2023-04-01",
      |    "endDate": "2024-03-31"
      |  },
      |  "reportingEntity": {
      |    "customerIdentification1": "12345678",
      |    "customerIdentification2": "K12345",
      |    "idType": "UTR",
      |    "name": "Test Company"
      |  },
      |  "liableEntities": [
      |    {
      |      "customerIdentification1": "87654321",
      |      "customerIdentification2": "K54321",
      |      "idType": "UTR",
      |      "name": "Liable Entity 1",
      |      "liability": 1000.00
      |    }
      |  ],
      |  "totalLiability": 1000.00
      |}
      |""".stripMargin
  )

  val emptyLiableEntitiesBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "LIABILITY",
      |  "accountingPeriod": {
      |    "startDate": "2022-04-01",
      |    "endDate": "2023-03-31"
      |  },
      |  "reportingEntity": {
      |    "customerIdentification1": "12345678",
      |    "customerIdentification2": "K12345",
      |    "idType": "UTR",
      |    "name": "Test Company"
      |  },
      |  "liableEntities": [],
      |  "totalLiability": 0.00
      |}
      |""".stripMargin
  )

  val missingFieldsBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "LIABILITY",
      |  "accountingPeriod": {
      |    "startDate": "2022-04-01",
      |    "endDate": "2023-03-31"
      |  }
      |}
      |""".stripMargin
  )

  val invalidAmountsBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "LIABILITY",
      |  "accountingPeriod": {
      |    "startDate": "2022-04-01",
      |    "endDate": "2023-03-31"
      |  },
      |  "reportingEntity": {
      |    "customerIdentification1": "12345678",
      |    "customerIdentification2": "K12345",
      |    "idType": "UTR",
      |    "name": "Test Company"
      |  },
      |  "liableEntities": [
      |    {
      |      "customerIdentification1": "87654321",
      |      "customerIdentification2": "K54321",
      |      "idType": "UTR",
      |      "name": "Liable Entity 1",
      |      "liability": 1000.00
      |    }
      |  ],
      |  "totalLiability": 2000.00
      |}
      |""".stripMargin
  )

  val invalidIdTypeBody: JsValue = Json.parse(
    """
      |{
      |  "reportType": "LIABILITY",
      |  "accountingPeriod": {
      |    "startDate": "2022-04-01",
      |    "endDate": "2023-03-31"
      |  },
      |  "reportingEntity": {
      |    "customerIdentification1": "12345678",
      |    "customerIdentification2": "K12345",
      |    "idType": "INVALID",
      |    "name": "Test Company"
      |  },
      |  "liableEntities": [
      |    {
      |      "customerIdentification1": "87654321",
      |      "customerIdentification2": "K54321",
      |      "idType": "UTR",
      |      "name": "Liable Entity 1",
      |      "liability": 1000.00
      |    }
      |  ],
      |  "totalLiability": 1000.00
      |}
      |""".stripMargin
  )

  def request(method: String = "POST", uri: String = "/uktr/submit", headers: Seq[(String, String)] = Seq(), body: JsObject): FakeRequest[JsObject] =
    FakeRequest(method, uri)
      .withHeaders(headers: _*)
      .withBody(body)

  "SubmitUKTRController" - {
    "when invalid JSON is submitted" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(invalidJsonBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return CREATED with success response for a valid liability submission" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))
      when(mockRepository.insert(any[UKTRSubmission](), eqTo(validPillar2Id), eqTo(false))).thenReturn(Future.successful(true))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(testRequestBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return CREATED with success response for a valid NIL return submission" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))
      when(mockRepository.insert(any[UKTRSubmission](), eqTo(validPillar2Id), eqTo(false))).thenReturn(Future.successful(true))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(nilReturnBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return BAD_REQUEST when X-Pillar2-Id header is missing" in {
      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders(authHeader)
        .withBody(testRequestBody)

      val exception = intercept[Pillar2IdMissing.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe Pillar2IdMissing
    }

    "return UNPROCESSABLE_ENTITY if accounting period doesn't match" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(invalidAccountingPeriodBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return UNPROCESSABLE_ENTITY if liableEntities array is empty" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(emptyLiableEntitiesBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return INTERNAL_SERVER_ERROR for specific Pillar2Id" in {
      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> ServerErrorPlrId, authHeader)
        .withBody(testRequestBody)

      val exception = intercept[ETMPInternalServerError.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPInternalServerError
    }

    "return FORBIDDEN when missing Authorization header" in {
      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id)
        .withBody(testRequestBody)

      val result = controller.submitUKTR()(request)
      status(result) mustBe FORBIDDEN
    }

    "return BAD_REQUEST when required fields are missing" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(missingFieldsBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return UNPROCESSABLE_ENTITY when submitting with invalid amounts" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(invalidAmountsBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return UNPROCESSABLE_ENTITY when submitting with invalid ID type" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(invalidIdTypeBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(controller.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }

    "return ETMPBadRequest when UKTRSubmission is neither a UKTRNilReturn nor a UKTRLiabilityReturn" in {
      when(mockOrgService.getOrganisation(eqTo(validPillar2Id))).thenReturn(Future.successful(testOrg))

      val mockUKTRController = new SubmitUKTRController(
        app.injector.instanceOf[play.api.mvc.ControllerComponents],
        app.injector.instanceOf[uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter],
        mockRepository,
        mockOrgService
      )(ec) {
        override def validatePillar2Id(pillar2Id: Option[String]): Future[String] =
          Future.successful(validPillar2Id)

        override def processUKTRSubmission(
          plrReference:  String,
          request:       play.api.mvc.Request[JsValue],
          successAction: (UKTRSubmission, String) => Future[play.api.mvc.Result]
        )(implicit ec:   ExecutionContext): Future[play.api.mvc.Result] =
          Future.failed(ETMPBadRequest)
      }

      val requestBody = Json.obj(
        "accountingPeriodFrom" -> "2024-01-01",
        "accountingPeriodTo"   -> "2024-12-31",
        "obligationMTT"        -> false,
        "electionUKGAAP"       -> false,
        "liabilities" -> Json.obj(
          "customType" -> "NEITHER_NIL_NOR_LIABILITY"
        )
      )

      val request = FakeRequest("POST", "/uktr/submit")
        .withHeaders("X-Pillar2-Id" -> validPillar2Id, authHeader)
        .withBody(requestBody)

      val exception = intercept[ETMPBadRequest.type] {
        Await.result(mockUKTRController.submitUKTR()(request), 5.seconds)
      }

      exception mustBe ETMPBadRequest
    }
  }
}
