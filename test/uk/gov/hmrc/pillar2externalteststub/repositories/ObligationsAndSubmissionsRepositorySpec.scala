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
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmission
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

    "filter submissions based on accounting period overlap with query date range" in {
      val baseDate   = LocalDate.of(2023, 1, 1)
      val queryRange = (baseDate, baseDate.plusMonths(3))

      // Create test submissions with different date relationships to query range
      case class TestCase(name: String, dateRange: (LocalDate, LocalDate), shouldMatch: Boolean)

      val testCases = Seq(
        TestCase("before", (baseDate.minusMonths(6), baseDate.minusMonths(3)), false), // completely before
        TestCase("startOverlap", (baseDate.minusMonths(1), baseDate.plusDays(15)), true), // overlaps start of query
        TestCase("during", (baseDate.plusDays(5), baseDate.plusMonths(2)), true), // completely within query
        TestCase("endOverlap", (baseDate.plusMonths(2).plusDays(15), baseDate.plusMonths(4)), true), // overlaps end of query
        TestCase("after", (baseDate.plusMonths(4), baseDate.plusMonths(6)), false) // completely after query
      )

      // Insert all test submissions and map to their IDs
      val submissionMap = testCases.map { case TestCase(name, (from, to), _) =>
        val id = new ObjectId()
        val submission = validRequestBody
          .as[UKTRLiabilityReturn]
          .copy(
            accountingPeriodFrom = from,
            accountingPeriodTo = to
          )
        insertRequest(submission, validPlrId, id) shouldBe true
        name                                            -> id
      }.toMap

      // Execute query with test date range
      val results = findRequest(validPlrId, queryRange._1, queryRange._2)

      // Verify correct submissions are returned
      results.size shouldBe 3

      val resultIds        = results.map(_.submissionId).toSet
      val matchingCases    = testCases.filter(_.shouldMatch).map(tc => submissionMap(tc.name))
      val nonMatchingCases = testCases.filterNot(_.shouldMatch).map(tc => submissionMap(tc.name))

      // Verify each expected match is included
      matchingCases.foreach(id => resultIds should contain(id))

      // Verify each non-match is excluded
      nonMatchingCases.foreach(id => resultIds should not contain id)

      // Verify all submissions can be found with a wide date range
      findRequest(validPlrId, baseDate.minusYears(1), baseDate.plusYears(1)).size shouldBe 5
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
