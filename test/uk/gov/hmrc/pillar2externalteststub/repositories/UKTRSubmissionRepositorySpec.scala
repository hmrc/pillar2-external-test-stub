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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.MongoSupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.uktr.DetailedErrorResponse

import scala.concurrent.ExecutionContext

class UKTRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with ScalaFutures
    with MongoSupport
    with BeforeAndAfterEach
    with UKTRDataFixture {

  override implicit val patienceConfig: PatienceConfig = PatienceConfig(
    timeout = Span(5, Seconds),
    interval = Span(100, Millis)
  )

  val config = new AppConfig(Configuration.from(Map("appName" -> "pillar2-external-test-stub", "defaultDataExpireInDays" -> 28)))
  val app: Application = GuiceApplicationBuilder()
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .build()
  val repository: UKTRSubmissionRepository =
    new UKTRSubmissionRepository(config, mongoComponent)(app.injector.instanceOf[ExecutionContext])

  override def beforeEach(): Unit = {
    super.beforeEach()
    prepareDatabase()
  }

  override protected def prepareDatabase(): Unit = {
    repository.uktrRepo.collection.drop().toFuture().futureValue
    repository.subscriptionRepo.collection.drop().toFuture().futureValue
  }

  "UKTRSubmissionRepository" when {
    "handling valid submissions" should {
      "successfully insert a liability return" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe true
      }

      "successfully insert a nil return" in {
        repository.insert(nilSubmission, validPlrId).futureValue shouldBe true
      }

      "successfully handle amendments" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue

        repository.update(nilSubmission, validPlrId).futureValue.isRight shouldBe true
      }
    }

    "handling invalid submissions" should {
      "fail when attempting to update non-existent submission" in {
        val result: Either[DetailedErrorResponse, Boolean] = repository.update(liabilitySubmission, validPlrId).futureValue

        result.isLeft shouldBe true
      }
    }

    "handling subscriptions" should {
      "successfully insert and find a subscription" in {
        val subscription = validSubscription
        repository.insertSubscription(subscription).futureValue shouldBe true
        val found = repository.findByPLRReference(subscription.plrReference).futureValue
        found.isDefined shouldBe true
        found.get       shouldBe subscription
      }

      "return None when subscription not found" in {
        repository.findByPLRReference("nonexistent").futureValue shouldBe None
      }
    }
  }
}
