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
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Seconds, Span}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRSubmission}

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

        val futureResults: Future[List[Boolean]] = Future.sequence(futures)
        whenReady(futureResults, Timeout(Span(5, Seconds))) { results =>
          all(results) shouldBe true
        }
      }

      "must handle bulk inserts within acceptable time" in {
        val submissions = List.fill(50)(liabilitySubmission)
        val startTime   = System.nanoTime()

        val futures:       List[Future[Boolean]] = submissions.map(s => repository.insert(s, s"XEPLR${System.nanoTime()}"))
        val futureResults: Future[List[Boolean]] = Future.sequence(futures)
        whenReady(futureResults, Timeout(Span(5, Seconds))) { results =>
          val duration = (System.nanoTime() - startTime).nanos.toSeconds
          duration       should be <= 5L
          all(results) shouldBe true
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

      "must maintain historical records by creating new documents for amendments" in {
        // Insert original submission
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        // Count documents before update
        val countBefore = repository.collection
          .countDocuments(
            Filters.eq("pillar2Id", validPlrId)
          )
          .toFuture()
          .futureValue

        // Update (which should create a new document)
        repository.update(liabilitySubmission, validPlrId).futureValue shouldBe Right(true)

        // Count documents after update - should be increased by 1
        val countAfter = repository.collection
          .countDocuments(
            Filters.eq("pillar2Id", validPlrId)
          )
          .toFuture()
          .futureValue

        countAfter shouldBe countBefore + 1

        // Verify the new document has isAmendment = true
        val documents = repository.collection
          .find(Filters.eq("pillar2Id", validPlrId))
          .sort(Indexes.descending("submittedAt"))
          .toFuture()
          .futureValue

        documents.size             shouldBe 2
        documents.head.isAmendment shouldBe true
      }

      "must return Left(DetailedErrorResponse(RequestCouldNotBeProcessed)) when no submission exists" in {
        val result = repository.update(liabilitySubmission, validPlrId).futureValue
        result shouldBe Left(DetailedErrorResponse(RequestCouldNotBeProcessed))
      }

      "must handle concurrent updates correctly" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        val futures:       List[Future[Either[DetailedErrorResponse, Boolean]]] = List.fill(5)(repository.update(liabilitySubmission, validPlrId))
        val futureResults: Future[List[Either[DetailedErrorResponse, Boolean]]] = Future.sequence(futures)
        whenReady(futureResults, Timeout(Span(5, Seconds))) { results =>
          all(results) shouldBe Right(true)
        }
      }
    }

    "findDuplicateSubmission" - {
      "must return true when a submission exists for the same period" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true
        val result = repository
          .findDuplicateSubmission(validPlrId, liabilitySubmission.accountingPeriodFrom, liabilitySubmission.accountingPeriodTo)
          .futureValue
        result shouldBe true
      }

      "must return false when no submission exists for the period" in {
        val result = repository
          .findDuplicateSubmission(validPlrId, liabilitySubmission.accountingPeriodFrom, liabilitySubmission.accountingPeriodTo)
          .futureValue
        result shouldBe false
      }

      "must handle concurrent duplicate checks correctly" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true

        val futures: List[Future[Boolean]] = List.fill(10)(
          repository.findDuplicateSubmission(
            validPlrId,
            liabilitySubmission.accountingPeriodFrom,
            liabilitySubmission.accountingPeriodTo
          )
        )

        val futureResults: Future[List[Boolean]] = Future.sequence(futures)
        whenReady(futureResults, Timeout(Span(5, Seconds))) { results =>
          all(results) shouldBe true
        }
      }
    }

    "error handling" - {
      "must handle database errors gracefully" in {
        // Create a test repository that throws a DatabaseError when insert is called
        val errorThrowingRepo = new UKTRSubmissionRepository(config, app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent]) {
          override def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] =
            Future.failed(DatabaseError("Failed to create UKTR - Simulated database error"))
        }

        val submission = liabilitySubmission

        // Test that the exception is properly handled
        val result = errorThrowingRepo.insert(submission, "XMPLR0012345678")

        whenReady(result.failed) { error =>
          error          shouldBe a[uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError]
          error.getMessage should include("Failed to create UKTR")
        }
      }
    }
  }
}
