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

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfterEach
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Sorts

import scala.concurrent.ExecutionContext
import java.util.concurrent.TimeUnit

class UKTRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UKTRMongoSubmission]
    with IntegrationPatience
    with ScalaFutures
    with UKTRDataFixture
    with BeforeAndAfterEach {

  override protected lazy val collectionName = "uktr-submissions"

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

  implicit val ec: ExecutionContext = app.injector.instanceOf[ExecutionContext]

  override lazy val repository = new UKTRSubmissionRepository(config, mongoComponent)

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
  }

  override protected def prepareDatabase(): Unit = {

    repository.collection.drop().toFuture().futureValue

    val indexes = Seq(
      IndexModel(
        Indexes.compoundIndex(
          Indexes.ascending("pillar2Id"),
          Indexes.descending("submittedAt")
        ),
        IndexOptions().name("pillar2IdIndex").unique(true)
      ),
      IndexModel(
        Indexes.ascending("submittedAt"),
        IndexOptions()
          .name("submittedAtTTL")
          .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
      )
    )

    repository.collection.createIndexes(indexes).toFuture().futureValue
    ()
  }

  "UKTRSubmissionRepository" when {
    "handling submissions" should {
      "successfully insert a submission" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe true
      }

      "successfully insert an amendment" in {
        repository.insert(liabilitySubmission, pillar2Id, isAmendment = true).futureValue shouldBe true
      }

      "allow multiple submissions with same pillar2Id but different timestamps" in {

        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe true

        val firstResult = repository.findByPillar2Id(pillar2Id).futureValue
        firstResult               shouldBe defined
        firstResult.get.pillar2Id shouldBe pillar2Id

        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe true

        val submissions = repository.collection
          .find(Filters.equal("pillar2Id", pillar2Id))
          .sort(Sorts.descending("submittedAt"))
          .toFuture()
          .futureValue

        submissions.length                                                 shouldBe 2
        submissions.head.submittedAt.isAfter(submissions.last.submittedAt) shouldBe true
      }

      "successfully update a submission" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe true
        repository.update(nilSubmission, pillar2Id).futureValue       shouldBe Right(true)
      }

      "return Left when updating non-existent submission" in {
        repository.update(liabilitySubmission, "nonexistent").futureValue shouldBe Left(RequestCouldNotBeProcessed)
      }

      "find submission by pillar2Id" in {
        repository.insert(liabilitySubmission, pillar2Id).futureValue shouldBe true
        val result = repository.findByPillar2Id(pillar2Id).futureValue
        result               shouldBe defined
        result.get.pillar2Id shouldBe pillar2Id
        result.get.data      shouldBe liabilitySubmission
      }

      "return None when submission not found" in {
        repository.findByPillar2Id("nonexistent").futureValue shouldBe None
      }
    }
  }
}
