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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.uktr.DetailedErrorResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed

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

  private val repository = app.injector.instanceOf[UKTRSubmissionRepository]

  override def beforeEach(): Unit = {
    repository.uktrRepo.collection.drop().toFuture().futureValue
    ()
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

      "must return Left(DetailedErrorResponse(RequestCouldNotBeProcessed)) when no submission exists" in {
        val result = repository.update(liabilitySubmission, validPlrId).futureValue
        result shouldBe Left(DetailedErrorResponse(RequestCouldNotBeProcessed))
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
    }
  }
}
