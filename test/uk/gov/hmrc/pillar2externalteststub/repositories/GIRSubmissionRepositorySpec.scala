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

import org.bson.types.ObjectId
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.GIRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.mongo.GIRSubmission

import java.time.LocalDate
import scala.concurrent.ExecutionContext

class GIRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[GIRSubmission]
    with ScalaFutures
    with IntegrationPatience
    with GIRDataFixture {

  override protected val databaseName: String = "gir-submission-repository-spec"

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

  override protected val repository: GIRSubmissionRepository =
    app.injector.instanceOf[GIRSubmissionRepository]

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  private val testPillar2Id = "XMPLR0000000000"
  private val testRequest = GIRRequest(
    accountingPeriodFrom = LocalDate.of(2024, 1, 1),
    accountingPeriodTo = LocalDate.of(2024, 12, 31)
  )

  "insert" should {
    "successfully insert a new GIR submission" in {
      val result = repository.insert(testPillar2Id, testRequest).futureValue
      result shouldBe a[ObjectId]

      val submissions = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id            shouldBe testPillar2Id
      submission.accountingPeriodFrom shouldBe testRequest.accountingPeriodFrom
      submission.accountingPeriodTo   shouldBe testRequest.accountingPeriodTo
    }

    "allow submissions for same pillar2Id with different accounting periods" in {
      repository.insert(testPillar2Id, testRequest).futureValue shouldBe a[ObjectId]

      val differentPeriodRequest = testRequest.copy(
        accountingPeriodFrom = LocalDate.of(2025, 1, 1),
        accountingPeriodTo = LocalDate.of(2025, 12, 31)
      )
      repository.insert(testPillar2Id, differentPeriodRequest).futureValue shouldBe a[ObjectId]

      val submissions = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions.size shouldBe 2
    }

    "allow submissions for different pillar2Ids with same accounting period" in {
      repository.insert(testPillar2Id, testRequest).futureValue     shouldBe a[ObjectId]
      repository.insert("XMPLR0000000001", testRequest).futureValue shouldBe a[ObjectId]

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

    "return all submissions for a given pillar2Id" in {
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

      requests.foreach(request => repository.insert(testPillar2Id, request).futureValue shouldBe a[ObjectId])

      val submissions = repository.findByPillar2Id(testPillar2Id).futureValue
      submissions.size                      shouldBe 3
      submissions.map(_.accountingPeriodFrom) should contain theSameElementsAs requests.map(_.accountingPeriodFrom)
    }
  }

  "findByPillar2IdAndAccountingPeriod" should {
    "return None when no matching submission exists" in {
      repository
        .findByPillar2IdAndAccountingPeriod(
          "NONEXISTENT",
          LocalDate.of(2024, 1, 1),
          LocalDate.of(2024, 12, 31)
        )
        .futureValue shouldBe None
    }

    "return the matching submission when one exists" in {
      repository.insert(testPillar2Id, testRequest).futureValue shouldBe a[ObjectId]

      val submission = repository
        .findByPillar2IdAndAccountingPeriod(
          testPillar2Id,
          testRequest.accountingPeriodFrom,
          testRequest.accountingPeriodTo
        )
        .futureValue

      submission.isDefined                shouldBe true
      submission.get.pillar2Id            shouldBe testPillar2Id
      submission.get.accountingPeriodFrom shouldBe testRequest.accountingPeriodFrom
      submission.get.accountingPeriodTo   shouldBe testRequest.accountingPeriodTo
    }
  }

  "deleteByPillar2Id" should {
    "successfully delete all submissions for a given pillar2Id" in {
      repository.insert(testPillar2Id, testRequest).futureValue
      repository
        .insert(testPillar2Id, testRequest.copy(accountingPeriodFrom = LocalDate.of(2025, 1, 1)))
        .futureValue // Insert another to ensure deleteMany works

      repository.findByPillar2Id(testPillar2Id).futureValue.size shouldBe 2

      repository.deleteByPillar2Id(testPillar2Id).futureValue shouldBe true
      repository.findByPillar2Id(testPillar2Id).futureValue   shouldBe empty
    }

    "return true when attempting to delete non-existent pillar2Id" in {
      val deleteResult = repository.deleteByPillar2Id("NONEXISTENT").futureValue
      deleteResult shouldBe true
    }
  }
}
