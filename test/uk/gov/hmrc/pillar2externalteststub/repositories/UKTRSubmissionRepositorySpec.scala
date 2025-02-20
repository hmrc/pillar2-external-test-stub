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
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn
import uk.gov.hmrc.pillar2externalteststub.repositories.{OrganisationRepository, UKTRSubmissionRepository}
import org.mockito.Mockito.{mock, when}
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRDetailedError}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSubmission

import scala.concurrent.{ExecutionContext, Future}
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class UKTRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[JsObject]
    with IntegrationPatience
    with ScalaFutures
    with UKTRDataFixture
    with play.api.Logging {

  override protected lazy val collectionName = "uktr-submissions"

  override protected lazy val indexes = Seq(
    IndexModel(
      Indexes.ascending("pillar2Id"),
      IndexOptions()
        .name("uktr_submissions_pillar2Id_idx")
        .sparse(true)
        .background(true)
    ),
    IndexModel(
      Indexes.ascending("createdAt"),
      IndexOptions()
        .name("createdAtTTL")
        .expireAfter(28, TimeUnit.DAYS)
        .background(true)
    )
  )

  val config = new AppConfig(
    Configuration.from(
      Map(
        "appName"                 -> "pillar2-external-test-stub",
        "defaultDataExpireInDays" -> 28,
        "mongodb.uri"             -> s"mongodb://localhost:27017/test-${this.getClass.getSimpleName}"
      )
    )
  )

  val app: Application = GuiceApplicationBuilder()
    .overrides(play.api.inject.bind[MongoComponent].toInstance(mongoComponent))
    .build()

  val mockOrgRepository: OrganisationRepository = mock(classOf[OrganisationRepository])

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  override lazy val repository = new UKTRSubmissionRepository(config, mongoComponent, mockOrgRepository)

  val organisation = TestOrganisation(
    orgDetails = OrgDetails(
      domesticOnly = false,
      organisationName = "Test Org",
      registrationDate = LocalDate.of(2024, 1, 1)
    ),
    accountingPeriod = AccountingPeriod(
      startDate = LocalDate.of(2024, 1, 1),
      endDate = LocalDate.of(2024, 12, 31)
    ),
    lastUpdated = java.time.Instant.now()
  )

  val domesticOrganisation = organisation.copy(
    orgDetails = organisation.orgDetails.copy(domesticOnly = true)
  )

  override def beforeEach(): Unit = {
    super.beforeEach()

    // Drop all collections and recreate indexes
    prepareDatabase()

    // Reset mock behavior
    val _ = Seq(
      when(mockOrgRepository.findByPillar2Id("NONEXISTENT")).thenReturn(Future.successful(None)),
      when(mockOrgRepository.findByPillar2Id("DOMESTIC123"))
        .thenReturn(Future.successful(Some(TestOrganisationWithId("DOMESTIC123", domesticOrganisation)))),
      when(mockOrgRepository.findByPillar2Id(pillar2Id)).thenReturn(Future.successful(Some(TestOrganisationWithId(pillar2Id, organisation)))),
      when(mockOrgRepository.findByPillar2Id("TEST123")).thenReturn(Future.successful(Some(TestOrganisationWithId("TEST123", organisation))))
    )
  }

  override protected def prepareDatabase(): Unit = {
    // Drop existing collection
    mongoComponent.database.getCollection(collectionName).drop().toFuture().futureValue

    // Create collection with validator
    mongoComponent.database.createCollection(collectionName).toFuture().futureValue

    // Create indexes and wait for completion
    val indexCreationResult = repository.collection.createIndexes(indexes).toFuture().futureValue
    logger.info(s"Index creation result: $indexCreationResult")

    // Verify indexes
    val existingIndexes = repository.collection.listIndexes().toFuture().futureValue.toList
    val indexNames      = existingIndexes.map(_.get("name").map(_.asString().getValue).getOrElse("unnamed"))
    logger.info(s"Created indexes: ${indexNames.mkString(", ")}")

    // Verify TTL index specifically
    val ttlIndex = existingIndexes.find(_.get("name").map(_.asString().getValue).contains("createdAtTTL"))
    if (!ttlIndex.exists(_.get("expireAfterSeconds").isDefined)) {
      throw new IllegalStateException("TTL index not properly configured")
    }
  }

  // Helper method to compare error responses ignoring exact timestamp
  private def compareErrorResponses(actual: DetailedErrorResponse, expected: DetailedErrorResponse): Boolean =
    actual.errors.code == expected.errors.code &&
      actual.errors.text == expected.errors.text

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
        val result = repository.insert(liabilitySubmission, pillar2Id).futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Tax Obligation Already Fulfilled"
              )
            )
          )
        ) shouldBe Left(true)
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

        val result = failingRepository.insert(liabilitySubmission, pillar2Id).futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Failed to create UKTR - Simulated database error"
              )
            )
          )
        ) shouldBe Left(true)
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
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Organisation not found"
              )
            )
          )
        ) shouldBe Left(true)
      }

      "return Left with error for mismatched accounting period during update" in {
        val mismatchedSubmission = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1))
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val result = repository.update(mismatchedSubmission, pillar2Id).futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Accounting period does not match registered period"
              )
            )
          )
        ) shouldBe Left(true)
      }

      "return Left with error for MTT values in domestic only groups during update" in {
        val submissionWithMTT = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val result = repository.update(submissionWithMTT, "DOMESTIC123").futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Domestic only groups cannot have MTT values"
              )
            )
          )
        ) shouldBe Left(true)
      }
    }

    "validating against organization data" should {
      "fail when organization not found" in {
        val result = repository.insert(liabilitySubmission, "NONEXISTENT").futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Organisation not found"
              )
            )
          )
        ) shouldBe Left(true)
      }

      "fail for mismatched accounting period" in {
        val mismatchedSubmission = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(accountingPeriodFrom = lr.accountingPeriodFrom.plusYears(1))
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val result = repository.insert(mismatchedSubmission, pillar2Id).futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Accounting period does not match registered period"
              )
            )
          )
        ) shouldBe Left(true)
      }

      "fail for MTT values in domestic only groups" in {
        val submissionWithMTT = liabilitySubmission match {
          case lr: UKTRLiabilityReturn => lr.copy(obligationMTT = true)
          case _ => fail("Expected UKTRLiabilityReturn")
        }
        val result = repository.insert(submissionWithMTT, "DOMESTIC123").futureValue
        result.isLeft shouldBe true
        result.left.map(actual =>
          compareErrorResponses(
            actual,
            DetailedErrorResponse(
              UKTRDetailedError(
                processingDate = nowZonedDateTime,
                code = UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003,
                text = "Domestic only groups cannot have MTT values"
              )
            )
          )
        ) shouldBe Left(true)
      }
    }
  }
}
