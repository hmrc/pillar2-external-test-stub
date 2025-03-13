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
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.ObligationsAndSubmissionsMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn

import scala.concurrent.ExecutionContext
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture

class ObligationsAndSubmissionsRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[ObligationsAndSubmissionsMongoSubmission]
    with ScalaFutures
    with IntegrationPatience
    with UKTRDataFixture {

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

  override protected val repository: ObligationsAndSubmissionsRepository =
    app.injector.instanceOf[ObligationsAndSubmissionsRepository]

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  private val testSubmissionId = new ObjectId()
  private val testSubmission   = validRequestBody.as[UKTRLiabilityReturn]

  "insert" should {
    "successfully insert a new submission" in {
      val result = repository.insert(testSubmission, validPlrId, testSubmissionId).futureValue
      result shouldBe true

      val submissions = repository.findAllSubmissionsByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id      shouldBe validPlrId
      submission.submissionId   shouldBe testSubmissionId
      submission.submissionType shouldBe SubmissionType.UKTR
    }

    "allow multiple submissions for the same pillar2Id" in {
      repository.insert(testSubmission, validPlrId, new ObjectId()).futureValue shouldBe true
      repository.insert(testSubmission, validPlrId, new ObjectId()).futureValue shouldBe true

      val submissions = repository.findAllSubmissionsByPillar2Id(validPlrId).futureValue
      submissions.size shouldBe 2
    }
  }

  "findAllSubmissionsByPillar2Id" should {
    "return empty sequence when no submissions exist" in {
      repository.findAllSubmissionsByPillar2Id("NONEXISTENT").futureValue shouldBe empty
    }

    "return all submissions for a given pillar2Id" in {
      val objectIds = List(new ObjectId(), new ObjectId(), new ObjectId())

      objectIds.foreach { id =>
        repository.insert(testSubmission, validPlrId, id).futureValue shouldBe true
      }

      val submissions = repository.findAllSubmissionsByPillar2Id(validPlrId).futureValue
      submissions.size              shouldBe 3
      submissions.map(_.submissionId) should contain theSameElementsAs objectIds
    }
  }

  "deleteByPillar2Id" should {
    "successfully delete all submissions for a given pillar2Id" in {
      repository.insert(testSubmission, validPlrId, new ObjectId()).futureValue shouldBe true
      repository.insert(testSubmission, validPlrId, new ObjectId()).futureValue shouldBe true

      repository.findAllSubmissionsByPillar2Id(validPlrId).futureValue.size shouldBe 2

      repository.deleteByPillar2Id(validPlrId).futureValue             shouldBe true
      repository.findAllSubmissionsByPillar2Id(validPlrId).futureValue shouldBe empty
    }

    "return true when attempting to delete non-existent pillar2Id" in {
      repository.deleteByPillar2Id("NONEXISTENT").futureValue shouldBe true
    }
  }
}
