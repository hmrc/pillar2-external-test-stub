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
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.ObligationsAndSubmissionsMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn

import java.time.LocalDate

class ObligationsAndSubmissionsRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[ObligationsAndSubmissionsMongoSubmission]
    with ScalaFutures
    with IntegrationPatience
    with UKTRDataFixture {

  val config = new AppConfig(
    Configuration.from(Map("appName" -> "pillar2-external-test-stub", "defaultDataExpireInDays" -> 28))
  )

  private val app = GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"  -> false,
      "encryptionToggle" -> "true"
    )
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .build()

  override protected val repository: ObligationsAndSubmissionsRepository =
    app.injector.instanceOf[ObligationsAndSubmissionsRepository]

  def findRequest(
    pillar2Id: String = validPlrId,
    from:      LocalDate = accountingPeriod.startDate,
    to:        LocalDate = accountingPeriod.endDate
  ): Seq[ObligationsAndSubmissionsMongoSubmission] =
    repository.findByPillar2Id(pillar2Id, from, to).futureValue

  def insertRequest(
    submission: BaseSubmission = validRequestBody.as[UKTRLiabilityReturn],
    pillar2Id:  String = validPlrId,
    id:         ObjectId = new ObjectId()
  ): Boolean =
    repository.insert(submission, pillar2Id, id).futureValue

  "insert" should {
    "successfully insert a new submission" in {
      val testSubmissionId = new ObjectId()
      val result           = insertRequest(id = testSubmissionId)
      result shouldBe true

      val submissions = findRequest()
      submissions.size shouldBe 1
      val submission = submissions.head
      submission.pillar2Id      shouldBe validPlrId
      submission.submissionId   shouldBe testSubmissionId
      submission.submissionType shouldBe SubmissionType.UKTR
    }

    "allow multiple submissions for the same pillar2Id" in {
      insertRequest() shouldBe true
      insertRequest() shouldBe true

      val submissions = findRequest()
      submissions.size shouldBe 2
    }
  }

  "findByPillar2Id" should {
    "return empty sequence when no submissions exist" in {
      findRequest("NONEXISTENT") shouldBe empty
    }

    "return all submissions for a given pillar2Id" in {
      val objectIds = List(new ObjectId(), new ObjectId(), new ObjectId())

      objectIds.foreach(id => insertRequest(id = id) shouldBe true)

      val submissions = findRequest()
      submissions.size              shouldBe 3
      submissions.map(_.submissionId) should contain theSameElementsAs objectIds
    }

    "not return submissions when the dates queried do no cover their accounting period" in {
      insertRequest()
      val fromDate = LocalDate.of(2022, 1, 1)
      val toDate   = LocalDate.of(2022, 2, 1)

      repository.findByPillar2Id(validPlrId, fromDate, toDate).futureValue.isEmpty shouldBe true
      findRequest().isEmpty                                                        shouldBe false
    }
  }

  "deleteByPillar2Id" should {
    "successfully delete all submissions for a given pillar2Id" in {
      insertRequest()
      insertRequest()

      findRequest().size shouldBe 2

      repository.deleteByPillar2Id(validPlrId).futureValue shouldBe true
      findRequest()                                        shouldBe empty
    }

    "return true when attempting to delete non-existent pillar2Id" in {
      repository.deleteByPillar2Id("NONEXISTENT").futureValue shouldBe true
    }
  }
}
