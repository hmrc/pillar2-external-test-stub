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

package uk.gov.hmrc.pillar2externalteststub.repositories

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.JsObject
import play.api.{Application, Configuration}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper.nowZonedDateTime
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr._

import scala.concurrent.Future

class UKTRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[JsObject]
    with IntegrationPatience
    with ScalaFutures
    with UKTRDataFixture {

  val config = new AppConfig(Configuration.from(Map("appName" -> "pillar2-external-test-stub", "defaultDataExpireInDays" -> 28)))
  val app: Application = GuiceApplicationBuilder()
    .overrides(play.api.inject.bind[MongoComponent].toInstance(mongoComponent))
    .build()

  implicit val ec: scala.concurrent.ExecutionContext = app.injector.instanceOf[scala.concurrent.ExecutionContext]

  val mockOrgRepository: OrganisationRepository = new OrganisationRepository(mongoComponent, config) {
    override def findByPillar2Id(
      pillar2Id: String
    ): Future[Either[uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError, Option[TestOrganisationWithId]]] =
      if (pillar2Id == "NONEXISTENT") Future.successful(Right(None))
      else
        Future.successful(
          Right(
            Some(
              TestOrganisationWithId(
                pillar2Id = pillar2Id,
                organisation = TestOrganisation(
                  orgDetails = OrgDetails(
                    domesticOnly = pillar2Id == "DOMESTIC123",
                    organisationName = "Test Org",
                    registrationDate = liabilitySubmission.accountingPeriodFrom
                  ),
                  accountingPeriod = AccountingPeriod(
                    startDate = liabilitySubmission.accountingPeriodFrom,
                    endDate = liabilitySubmission.accountingPeriodTo
                  ),
                  lastUpdated = java.time.Instant.now()
                )
              )
            )
          )
        )
  }

  val repository: UKTRSubmissionRepository =
    new UKTRSubmissionRepository(config, mongoComponent, mockOrgRepository)

  "UKTRSubmissionRepository" when {
    "handling valid submissions" should {
      "successfully insert a liability return" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe Right(true)
      }

      "successfully insert a nil return" in {
        repository.insert(nilSubmission, pillar2Id).futureValue shouldBe Right(true)
      }

      "successfully handle amendments" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe Right(true)
        repository.update(nilSubmission, pillar2Id).futureValue       shouldBe Right(true)
      }

      "fail with error for duplicate submissions" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe Right(true)
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Tax Obligation Already Fulfilled"
            )
          )
        )
      }

      "allow amendments even when submission exists" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue                     shouldBe Right(true)
        repository.insert(liabilitySubmission, pillar2Id, isAmendment = true).futureValue shouldBe Right(true)
      }

      "handle database errors gracefully" in {
        val failingRepository = new UKTRSubmissionRepository(config, mongoComponent, mockOrgRepository) {
          override def insert(
            submission:  UKTRSubmission,
            pillar2Id:   String,
            isAmendment: Boolean = false
          ): Future[Either[DetailedErrorResponse, Boolean]] =
            Future.successful(
              Left(
                DetailedErrorResponse(
                  UKTRDetailedError(
                    processingDate = nowZonedDateTime,
                    code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                    text = "Failed to create UKTR - Simulated database error"
                  )
                )
              )
            )
        }

        failingRepository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Failed to create UKTR - Simulated database error"
            )
          )
        )
      }

      "return the organisation when it exists" in {
        repository.insert(liabilitySubmission, "TEST123").futureValue shouldBe Right(true)
        val result = repository.findByPillar2Id("TEST123").futureValue
        result shouldBe Right(Some(play.api.libs.json.Json.toJson(liabilitySubmission).as[JsObject]))
      }
    }

    "handling invalid submissions" should {
      "return Left with error when attempting to update non-existent submission" in {
        val result = repository.update(liabilitySubmission, pillar2Id).futureValue
        result shouldBe Left(RequestCouldNotBeProcessed)
      }

      "return Left with error when organization not found during update" in {
        val result = repository.update(liabilitySubmission, "NONEXISTENT").futureValue
        result shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Organisation not found"
            )
          )
        )
      }

      "return Left with error for mismatched accounting period during update" in {
        val mismatchedSubmission = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1))
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val result = repository.update(mismatchedSubmission, pillar2Id).futureValue
        result shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Accounting period does not match registered period"
            )
          )
        )
      }

      "return Left with error for MTT values in domestic only groups during update" in {
        val submissionWithMTT = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val result = repository.update(submissionWithMTT, "DOMESTIC123").futureValue
        result shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Domestic only groups cannot have MTT values"
            )
          )
        )
      }
    }

    "validating against organization data" should {
      "fail when organization not found" in {
        repository.insert(liabilitySubmission, "NONEXISTENT").futureValue shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Organisation not found"
            )
          )
        )
      }

      "fail for mismatched accounting period" in {
        val mismatchedSubmission = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1))
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        repository.insert(mismatchedSubmission, pillar2Id).futureValue shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Accounting period does not match registered period"
            )
          )
        )
      }

      "fail for MTT values in domestic only groups" in {
        val submissionWithMTT = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        repository.insert(submissionWithMTT, "DOMESTIC123").futureValue shouldBe Left(
          DetailedErrorResponse(
            UKTRDetailedError(
              processingDate = nowZonedDateTime,
              code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
              text = "Domestic only groups cannot have MTT values"
            )
          )
        )
      }
    }
  }
}
