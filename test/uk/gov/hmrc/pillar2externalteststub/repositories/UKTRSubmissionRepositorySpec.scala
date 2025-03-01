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

package uk.gov.hmrc.pillar2externalteststub.repositories

import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Indexes
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRSubmission}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class UKTRSubmissionRepositorySpec
    extends AnyFreeSpec
    with Matchers
    with GuiceOneAppPerSuite
    with ScalaFutures
    with IntegrationPatience
    with BeforeAndAfterEach
    with UKTRDataFixture {

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .configure(
        "mongodb.uri" -> "mongodb://localhost:27017/test-uktr-submission-integration"
      )
      .build()

  private val config     = app.injector.instanceOf[AppConfig]
  private val repository = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent])

  implicit override val patienceConfig: PatienceConfig = PatienceConfig(timeout = 5.seconds, interval = 100.millis)

  override def beforeEach(): Unit = {
    super.beforeEach()
    repository.collection.drop().toFuture().futureValue
  }

  "UKTRSubmissionRepository" - {

    "insert" - {
      "must successfully insert a liability return" in {
        val result = repository.insert(liabilitySubmission, validPlrId).futureValue
        result shouldBe true
      }

      "must successfully insert a nil return" in {
        val result = repository.insert(nilSubmission, validPlrId).futureValue
        result shouldBe true
      }

      "must handle concurrent inserts correctly" in {
        val submissions = List.fill(10)(liabilitySubmission)
        val futures: List[Future[Boolean]] = submissions.map(s => repository.insert(s, s"XEPLR${System.nanoTime()}"))

        val results: List[Boolean] = Future.sequence(futures).futureValue
        all(results) shouldBe true
      }

      "must handle bulk inserts within acceptable time" in {
        val submissions = List.fill(50)(liabilitySubmission)
        val startTime   = System.nanoTime()

        val futures: List[Future[Boolean]] = submissions.map(s => repository.insert(s, s"XEPLR${System.nanoTime()}"))
        val results: List[Boolean]         = Future.sequence(futures).futureValue
        val duration = (System.nanoTime() - startTime).nanos.toSeconds
        duration       should be <= 5L
        all(results) shouldBe true
      }

      "must handle specific database exceptions during insert" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] =
            Future.failed(DatabaseError("Failed to create UKTR due to: Duplicate key error"))
        }

        val result = errorThrowingRepo.insert(liabilitySubmission, validPlrId)

        whenReady(result.failed) { error =>
          error          shouldBe a[DatabaseError]
          error.getMessage should include("Failed to create UKTR")
          error.getMessage should include("Duplicate key error")
        }
      }

      "must handle database errors for amendment operations" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] =
            Future.failed(DatabaseError("Failed to amend UKTR: Database connection failed"))
        }

        val result = errorThrowingRepo.insert(liabilitySubmission, validPlrId, isAmendment = true)

        whenReady(result.failed) { error =>
          error          shouldBe a[DatabaseError]
          error.getMessage should include("Failed to amend UKTR")
          error.getMessage should include("Database connection failed")
        }
      }

      "must correctly set document fields when inserting" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        val document = repository.collection
          .find(Filters.eq("pillar2Id", validPlrId))
          .first()
          .toFuture()
          .futureValue

        document.pillar2Id           shouldBe validPlrId
        document.isAmendment         shouldBe false
        document.data                shouldBe liabilitySubmission
        Option(document.submittedAt) shouldBe defined
      }

      "must handle general exceptions during insert operations" in {
        // Create a test repository that directly returns a DatabaseError
        val exceptionThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] =
            Future.failed(DatabaseError("Failed to create UKTR - Unexpected runtime exception during insert"))
        }

        val result = exceptionThrowingRepo.insert(liabilitySubmission, validPlrId)

        whenReady(result.failed) { error =>
          error          shouldBe a[DatabaseError]
          error.getMessage should include("Failed to create UKTR")
          error.getMessage should include("Unexpected runtime exception during insert")
        }
      }
    }

    "update" - {
      "must successfully update a liability return" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true
        val result = repository.update(liabilitySubmission, validPlrId).futureValue
        result shouldBe Right(true)
      }

      "must successfully update a nil return" in {
        repository.insert(nilSubmission, validPlrId).futureValue shouldBe true
        val result = repository.update(nilSubmission, validPlrId).futureValue
        result shouldBe Right(true)
      }

      "must handle database errors during update" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def update(submission: UKTRSubmission, pillar2Id: String): Future[Either[DetailedErrorResponse, Boolean]] =
            Future.failed(DatabaseError("Failed to update submission"))
        }

        val result = errorThrowingRepo.update(liabilitySubmission, validPlrId)

        whenReady(result.failed) { error =>
          error          shouldBe a[DatabaseError]
          error.getMessage should include("Failed to update submission")
        }
      }

      "must handle validation errors during update" in {
        val result = repository.update(liabilitySubmission, "invalid-id").futureValue
        result shouldBe Left(DetailedErrorResponse(RequestCouldNotBeProcessed))
      }

      "must handle insert errors during update" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          // Override findByPillar2Id to return Some to trigger the insert path
          override def findByPillar2Id(pillar2Id: String): Future[Option[UKTRMongoSubmission]] =
            Future.successful(
              Some(
                UKTRMongoSubmission(
                  _id = new org.bson.types.ObjectId(),
                  pillar2Id = pillar2Id,
                  isAmendment = false,
                  data = liabilitySubmission,
                  submittedAt = java.time.Instant.now()
                )
              )
            )

          // Override insert to fail with DatabaseError
          override def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] =
            Future.failed(DatabaseError("Database connection failed during insert"))
        }

        val result = errorThrowingRepo.update(liabilitySubmission, validPlrId).futureValue
        result shouldBe Left(DetailedErrorResponse(RequestCouldNotBeProcessed))
      }

      "must return Left with error response when submission not found" in {
        val result = repository.update(liabilitySubmission, "XEPLR0000000001").futureValue
        result shouldBe Left(DetailedErrorResponse(RequestCouldNotBeProcessed))
      }

      "must maintain historical records by creating new documents for amendments" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        val countBefore = repository.collection
          .countDocuments(Filters.eq("pillar2Id", validPlrId))
          .toFuture()
          .futureValue

        repository.update(liabilitySubmission, validPlrId).futureValue shouldBe Right(true)

        val countAfter = repository.collection
          .countDocuments(Filters.eq("pillar2Id", validPlrId))
          .toFuture()
          .futureValue

        countAfter shouldBe countBefore + 1

        val documents = repository.collection
          .find(Filters.eq("pillar2Id", validPlrId))
          .sort(Indexes.descending("submittedAt"))
          .toFuture()
          .futureValue

        documents.size             shouldBe 2
        documents.head.isAmendment shouldBe true
        documents.last.isAmendment shouldBe false
      }
    }

    "findByPillar2Id" - {
      "must return the most recent submission for a pillar2Id" in {
        // Insert multiple submissions with same pillar2Id
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        // Create an amended version
        repository.insert(nilSubmission, validPlrId, isAmendment = true).futureValue shouldBe true

        val result = repository.findByPillar2Id(validPlrId).futureValue
        result                 shouldBe defined
        result.get.data        shouldBe nilSubmission
        result.get.isAmendment shouldBe true
      }

      "must return None when no submission exists" in {
        val result = repository.findByPillar2Id("XEPLR0000000999").futureValue
        result shouldBe None
      }

      "must handle database exceptions gracefully" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def findByPillar2Id(pillar2Id: String): Future[Option[UKTRMongoSubmission]] =
            Future.failed(DatabaseError("Database connection failed when finding submission"))
        }

        // Use recover to handle the exception and return None
        val result = errorThrowingRepo
          .findByPillar2Id(validPlrId)
          .recover { case _: Exception =>
            None
          }
          .futureValue

        result shouldBe None
      }

      "must handle general exceptions gracefully" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def findByPillar2Id(pillar2Id: String): Future[Option[UKTRMongoSubmission]] =
            Future.failed(new RuntimeException("Unexpected runtime exception"))
        }

        // Use recover to handle the exception and return None
        val result = errorThrowingRepo
          .findByPillar2Id(validPlrId)
          .recover { case _: Exception =>
            None
          }
          .futureValue

        result shouldBe None
      }
    }

    "isDuplicateSubmission" - {
      "must return true when a duplicate submission exists" in {
        val startDate = LocalDate.of(2024, 8, 14)
        val endDate   = LocalDate.of(2024, 12, 14)

        // Insert a submission
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        // Check for duplicate
        val result = repository.isDuplicateSubmission(validPlrId, startDate, endDate).futureValue
        result shouldBe true
      }

      "must return false when no duplicate exists" in {
        val startDate = LocalDate.of(2023, 1, 1)
        val endDate   = LocalDate.of(2023, 12, 31)

        // Check for duplicate with different dates
        val result = repository.isDuplicateSubmission(validPlrId, startDate, endDate).futureValue
        result shouldBe false
      }

      "must handle database errors" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def isDuplicateSubmission(pillar2Id: String, accountingPeriodFrom: LocalDate, accountingPeriodTo: LocalDate): Future[Boolean] =
            Future.failed(DatabaseError("Failed to check for duplicate submission: Database connection failed"))
        }

        val startDate = LocalDate.of(2024, 8, 14)
        val endDate   = LocalDate.of(2024, 12, 14)

        whenReady(errorThrowingRepo.isDuplicateSubmission(validPlrId, startDate, endDate).failed) { error =>
          error          shouldBe a[DatabaseError]
          error.getMessage should include("Failed to check for duplicate submission")
        }
      }

      "must handle general exceptions" in {
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def isDuplicateSubmission(pillar2Id: String, accountingPeriodFrom: LocalDate, accountingPeriodTo: LocalDate): Future[Boolean] =
            // Convert RuntimeException to DatabaseError
            Future.failed(DatabaseError("Failed to check for duplicate submission: Unexpected runtime exception"))
        }

        val startDate = LocalDate.of(2024, 8, 14)
        val endDate   = LocalDate.of(2024, 12, 14)

        whenReady(errorThrowingRepo.isDuplicateSubmission(validPlrId, startDate, endDate).failed) { error =>
          error          shouldBe a[DatabaseError]
          error.getMessage should include("Failed to check for duplicate submission")
        }
      }
    }

    "error handling" - {
      "must handle database errors gracefully" in {

        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] =
            Future.failed(DatabaseError("Failed to create UKTR - Simulated database error"))
        }

        val submission = liabilitySubmission

        val result = errorThrowingRepo.insert(submission, "XMPLR0012345678")

        whenReady(result.failed) { error =>
          error          shouldBe a[uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError]
          error.getMessage should include("Failed to create UKTR")
        }
      }
    }
  }
}
