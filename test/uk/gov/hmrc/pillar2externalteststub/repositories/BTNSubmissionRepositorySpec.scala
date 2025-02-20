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
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.btn.mongo.BTNSubmission

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class BTNSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[BTNSubmission]
    with ScalaFutures
    with IntegrationPatience {

  override protected val databaseName: String = "btn-submission-repository"

  val config = new AppConfig(
    Configuration.from(
      Map(
        "appName"                 -> "pillar2-external-test-stub",
        "defaultDataExpireInDays" -> 28
      )
    )
  )

  private val app = GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"  -> false,
      "encryptionToggle" -> "true"
    )
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent)
    )
    .build()

  override protected val repository: BTNSubmissionRepository =
    app.injector.instanceOf[BTNSubmissionRepository]

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  private val testPillar2Id = "XMPLR0000000000"
  private val testRequest = BTNRequest(
    accountingPeriodFrom = LocalDate.of(2024, 1, 1),
    accountingPeriodTo = LocalDate.of(2024, 12, 31)
  )

  "insert" should {
    "successfully insert a new BTN submission" in {
      val result = repository.insert(testPillar2Id, testRequest).futureValue
      result shouldBe true

      val submissions = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id            shouldBe testPillar2Id
      submission.accountingPeriodFrom shouldBe testRequest.accountingPeriodFrom
      submission.accountingPeriodTo   shouldBe testRequest.accountingPeriodTo
    }

    "allow submissions for same validPlrId with different accounting periods" in {
      repository.insert(testPillar2Id, testRequest).futureValue shouldBe true

      val differentPeriodRequest = testRequest.copy(
        accountingPeriodFrom = LocalDate.of(2025, 1, 1),
        accountingPeriodTo = LocalDate.of(2025, 12, 31)
      )
      repository.insert(testPillar2Id, differentPeriodRequest).futureValue shouldBe true

      val submissions = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions.size shouldBe 2
    }

    "allow submissions for different pillar2Ids with same accounting period" in {
      repository.insert(testPillar2Id, testRequest).futureValue     shouldBe true
      repository.insert("XMPLR0000000001", testRequest).futureValue shouldBe true

      val submissions1 = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions1.size shouldBe 1

      val submissions2 = repository.findByPillar2Id("XMPLR0000000001").futureValue
      submissions2.size shouldBe 1
    }
  }

  "findByPillar2Id" should {
    "return empty sequence when no submissions exist" in {
      repository.findByPillar2Id("NONEXISTENT").futureValue shouldBe empty
    }

    "return all submissions for a given validPlrId" in {
      val requests = List(
        testRequest,
        testRequest.copy(
          accountingPeriodFrom = LocalDate.of(2025, 1, 1),
          accountingPeriodTo = LocalDate.of(2025, 12, 31)
        ),
        testRequest.copy(
          accountingPeriodFrom = LocalDate.of(2026, 1, 1),
          accountingPeriodTo = LocalDate.of(2026, 12, 31)
        )
      )

      requests.foreach(request => repository.insert(testPillar2Id, request).futureValue shouldBe true)

      val submissions = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions.size                      shouldBe 3
      submissions.map(_.accountingPeriodFrom) should contain theSameElementsAs requests.map(_.accountingPeriodFrom)
    }
  }
}
