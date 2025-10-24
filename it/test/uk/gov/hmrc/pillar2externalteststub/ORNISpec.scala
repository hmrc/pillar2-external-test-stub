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

package uk.gov.hmrc.pillar2externalteststub

import org.mockito.ArgumentMatchers.{eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.{Application, inject}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.helpers.{ORNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.orn.mongo.ORNSubmission
import uk.gov.hmrc.pillar2externalteststub.models.response.HIPErrorResponse
import uk.gov.hmrc.pillar2externalteststub.models.response.Origin.HIP
import uk.gov.hmrc.pillar2externalteststub.repositories.{ORNSubmissionRepository, ObligationsAndSubmissionsRepository}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import scala.concurrent.{ExecutionContext, Future}

class ORNISpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with GuiceOneServerPerSuite
    with DefaultPlayMongoRepositorySupport[ORNSubmission]
    with BeforeAndAfterEach
    with ORNDataFixture
    with TestOrgDataFixture {

  override protected val databaseName: String = "test-orn-integration"

  private val httpClient = app.injector.instanceOf[HttpClientV2]
  private val baseUrl    = s"http://localhost:$port"
  private val ornRepository: ORNSubmissionRepository             = app.injector.instanceOf[ORNSubmissionRepository]
  private val oasRepository: ObligationsAndSubmissionsRepository = app.injector.instanceOf[ObligationsAndSubmissionsRepository]
  given ec:           ExecutionContext                    = app.injector.instanceOf[ExecutionContext]
  given hc:           HeaderCarrier                       = HeaderCarrier()
  override protected val repository = ornRepository

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri"             -> s"mongodb://localhost:27017/$databaseName",
        "metrics.enabled"         -> false,
        "defaultDataExpireInDays" -> 28
      )
      .overrides(inject.bind[OrganisationService].toInstance(mockOrgService))
      .build()

  def submitORN(pillar2Id: String, request: ORNRequest): HttpResponse =
    httpClient
      .post(url"$baseUrl/RESTAdapter/plr/overseas-return-notification")
      .transform(_.withHttpHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue

  def amendORN(pillar2Id: String, request: ORNRequest): HttpResponse =
    httpClient
      .put(url"$baseUrl/RESTAdapter/plr/overseas-return-notification")
      .transform(_.withHttpHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*))
      .withBody(Json.toJson(request))
      .execute[HttpResponse]
      .futureValue

  def getORN(pillar2Id: String, accountingPeriodFrom: String, accountingPeriodTo: String): HttpResponse =
    httpClient
      .get(
        url"$baseUrl/RESTAdapter/plr/overseas-return-notification?accountingPeriodFrom=$accountingPeriodFrom&accountingPeriodTo=$accountingPeriodTo"
      )
      .transform(_.withHttpHeaders(hipHeaders :+ ("X-Pillar2-Id" -> pillar2Id): _*))
      .execute[HttpResponse]
      .futureValue

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
  }

  override protected def prepareDatabase(): Unit = {
    ornRepository.collection.drop().toFuture().futureValue
    ornRepository.collection.createIndexes(ornRepository.indexes).toFuture().futureValue
    oasRepository.collection.drop().toFuture().futureValue
    oasRepository.collection.createIndexes(oasRepository.indexes).toFuture().futureValue
    ()
  }

  "ORN submission endpoint" should {
    "successfully save and retrieve ORN submissions" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val response = submitORN(validPlrId, validORNRequest)
      response.status shouldBe 201

      val submissions = ornRepository.findByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id            shouldBe validPlrId
      submission.accountingPeriodFrom shouldBe validORNRequest.accountingPeriodFrom
      submission.accountingPeriodTo   shouldBe validORNRequest.accountingPeriodTo
    }

    "save ORN submissions to both ORN and ObligationsAndSubmissions repositories" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val response = submitORN(validPlrId, validORNRequest)
      response.status shouldBe 201

      // Verify in ORN repository
      val ornSubmissions = ornRepository.findByPillar2Id(validPlrId).futureValue
      ornSubmissions.size shouldBe 1

      // Verify in OAS repository
      val oasSubmissions = oasRepository
        .findByPillar2Id(
          validPlrId,
          validORNRequest.accountingPeriodFrom,
          validORNRequest.accountingPeriodTo
        )
        .futureValue

      oasSubmissions.size shouldBe 1
      val oasSubmission = oasSubmissions.head
      oasSubmission.pillar2Id                  shouldBe validPlrId
      oasSubmission.accountingPeriod.startDate shouldBe validORNRequest.accountingPeriodFrom
      oasSubmission.accountingPeriod.endDate   shouldBe validORNRequest.accountingPeriodTo
      oasSubmission.submissionType             shouldBe SubmissionType.ORN_CREATE
    }

    "return 422 with tax obligation already fulfilled when submitting duplicate ORN" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First submission
      val firstResponse = submitORN(validPlrId, validORNRequest)
      firstResponse.status shouldBe 201

      // Second submission with same accounting period
      val secondResponse = submitORN(validPlrId, validORNRequest)
      secondResponse.status shouldBe 422
      val json = Json.parse(secondResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "044"
      (json \ "errors" \ "text").as[String] shouldBe "Tax obligation already fulfilled"
    }

    "successfully amend an existing ORN submission" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val submitResponse = submitORN(validPlrId, validORNRequest)
      submitResponse.status shouldBe 201

      val amendedRequest = validORNRequest.copy(
        reportingEntityName = "Updated Newco PLC"
      )
      val amendResponse = amendORN(validPlrId, amendedRequest)
      amendResponse.status shouldBe 200

      val submissions = ornRepository.findByPillar2Id(validPlrId).futureValue
      submissions.size                     shouldBe 2
      submissions.last.reportingEntityName shouldBe "Updated Newco PLC"
    }

    "save amended ORN submissions to both ORN and ObligationsAndSubmissions repositories" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First submit
      val submitResponse = submitORN(validPlrId, validORNRequest)
      submitResponse.status shouldBe 201

      // Then amend
      val amendedRequest = validORNRequest.copy(
        reportingEntityName = "Updated Newco PLC"
      )
      val amendResponse = amendORN(validPlrId, amendedRequest)
      amendResponse.status shouldBe 200

      // Verify in ORN repository - should have both original and amended submissions
      val ornSubmissions = ornRepository.findByPillar2Id(validPlrId).futureValue
      ornSubmissions.size shouldBe 2

      // Verify in OAS repository - should have both original and amended submissions
      val oasSubmissions = oasRepository
        .findByPillar2Id(
          validPlrId,
          validORNRequest.accountingPeriodFrom,
          validORNRequest.accountingPeriodTo
        )
        .futureValue

      oasSubmissions.size shouldBe 2
      oasSubmissions.foreach { submission =>
        submission.pillar2Id                  shouldBe validPlrId
        submission.accountingPeriod.startDate shouldBe validORNRequest.accountingPeriodFrom
        submission.accountingPeriod.endDate   shouldBe validORNRequest.accountingPeriodTo
        submission.submissionType match {
          case SubmissionType.ORN_CREATE => true shouldBe true // First submission
          case SubmissionType.ORN_AMEND  => true shouldBe true // Amended submission
          case _                         => fail(s"Unexpected submission type: ${submission.submissionType}")
        }
      }
    }

    "return 422 when attempting to amend non-existent ORN" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val amendResponse = amendORN(validPlrId, validORNRequest)
      amendResponse.status shouldBe 422
      val json = Json.parse(amendResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "003"
      (json \ "errors" \ "text").as[String] shouldBe "Request could not be processed"

      val submissions = ornRepository.findByPillar2Id(validPlrId).futureValue
      submissions shouldBe empty
    }

    "handle invalid requests appropriately" in {
      val responseWithoutId = httpClient
        .post(url"$baseUrl/RESTAdapter/plr/overseas-return-notification")
        .transform(_.withHttpHeaders(hipHeaders: _*))
        .withBody(Json.toJson(validORNRequest))
        .execute[HttpResponse]
        .futureValue

      responseWithoutId.status shouldBe 422
      val json = Json.parse(responseWithoutId.body)
      (json \ "errors" \ "code").as[String] shouldBe "089"

      ornRepository.findByPillar2Id(validPlrId).futureValue shouldBe empty
    }

    "handle server error cases correctly" in {
      val response = submitORN(serverErrorPlrId, validORNRequest)

      response.status shouldBe 500
      val json = Json.parse(response.body)
      (json \ "error" \ "code").as[String] shouldBe "500"

      ornRepository.findByPillar2Id(serverErrorPlrId).futureValue shouldBe empty
    }

    "handle non-existent organisation" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val response = submitORN(validPlrId, validORNRequest)
      response.status shouldBe 422
      val json = Json.parse(response.body)
      (json \ "errors" \ "code").as[String] shouldBe "063"
    }
  }

  "GET ORN endpoint" should {
    "successfully retrieve an existing ORN submission" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      // First create a submission
      val submitResponse = submitORN(validPlrId, validORNRequest)
      submitResponse.status shouldBe 201

      // Then retrieve it
      val getResponse = getORN(validPlrId, validORNRequest.accountingPeriodFrom.toString, validORNRequest.accountingPeriodTo.toString)
      getResponse.status shouldBe 200

      val submission = Json.parse(getResponse.body)
      (submission \ "success" \ "accountingPeriodFrom").as[String] shouldBe validORNRequest.accountingPeriodFrom.toString
      (submission \ "success" \ "accountingPeriodTo").as[String]   shouldBe validORNRequest.accountingPeriodTo.toString
      (submission \ "success" \ "filedDateGIR").as[String]         shouldBe validORNRequest.filedDateGIR.toString
      (submission \ "success" \ "countryGIR").as[String]           shouldBe validORNRequest.countryGIR
      (submission \ "success" \ "reportingEntityName").as[String]  shouldBe validORNRequest.reportingEntityName
      (submission \ "success" \ "TIN").as[String]                  shouldBe validORNRequest.TIN
      (submission \ "success" \ "issuingCountryTIN").as[String]    shouldBe validORNRequest.issuingCountryTIN
      (submission \ "success" \ "processingDate").as[String]         should not be empty
    }

    "return 422 when no submission exists for the given period" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val getResponse = getORN(validPlrId, "2025-01-01", "2025-12-31")
      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "005"
      (json \ "errors" \ "text").as[String] shouldBe "No Form Bundle found"
    }

    "return 400 when dates are invalid" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.successful(organisationWithId))

      val getResponse = getORN(validPlrId, "invalid-date", "2025-12-31")
      getResponse.status shouldBe 400
      val response = Json.parse(getResponse.body).as[HIPErrorResponse]
      response.origin shouldEqual HIP
      response.response.failures should have size 1
      response.response.failures.head.reason shouldEqual "json error"
    }

    "return 422 when ID number is missing" in {
      val getResponse = httpClient
        .get(url"$baseUrl/RESTAdapter/plr/overseas-return-notification?accountingPeriodFrom=2024-01-01&accountingPeriodTo=2024-12-31")
        .transform(_.withHttpHeaders(hipHeaders: _*))
        .execute[HttpResponse]
        .futureValue

      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "089"
    }

    "return 422 when organisation does not exist" in {
      when(mockOrgService.getOrganisation(eqTo(validPlrId))).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

      val getResponse = getORN(validPlrId, "2024-01-01", "2024-12-31")
      getResponse.status shouldBe 422
      val json = Json.parse(getResponse.body)
      (json \ "errors" \ "code").as[String] shouldBe "063"
    }

    "return 500 for server error PLR ID" in {
      val getResponse = getORN(serverErrorPlrId, "2024-01-01", "2024-12-31")
      getResponse.status shouldBe 500
      val json = Json.parse(getResponse.body)
      (json \ "error" \ "code").as[String]    shouldBe "500"
      (json \ "error" \ "message").as[String] shouldBe "Internal server error"
      (json \ "error" \ "logID").as[String]   shouldBe "C0000000000000000000000000000500"
    }
  }
}
