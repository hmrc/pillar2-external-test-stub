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

import org.mongodb.scala.bson.ObjectId
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission

import scala.concurrent.ExecutionContext

class UKTRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UKTRMongoSubmission]
    with IntegrationPatience
    with ScalaFutures
    with UKTRDataFixture {

  val config = new AppConfig(Configuration.from(Map("appName" -> "pillar2-external-test-stub", "defaultDataExpireInDays" -> 28)))
  val app: Application = GuiceApplicationBuilder()
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .build()
  val repository: UKTRSubmissionRepository =
    new UKTRSubmissionRepository(config, mongoComponent)(app.injector.instanceOf[ExecutionContext])

  "UKTRSubmissionRepository" when {
    "handling valid submissions" should {
      "successfully insert a liability return" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue shouldBe a[ObjectId]
      }

      "successfully insert a nil return" in {
        repository.insert(nilSubmission, validPlrId).futureValue shouldBe a[ObjectId]
      }

      "successfully handle amendments" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue

        repository.update(nilSubmission, validPlrId).futureValue._1.isInstanceOf[ObjectId] shouldBe true
        repository.update(nilSubmission, validPlrId).futureValue._2                        shouldBe None
      }
    }

    "handling invalid submissions" should {
      "fail when attempting to update non-existent submission" in {
        val result = repository.update(liabilitySubmission, validPlrId)

        result.failed.futureValue shouldBe a[RequestCouldNotBeProcessed.type]
      }
    }

    "handling deletions" should {
      "successfully delete all submissions for a given pillar2Id" in {
        repository.insert(liabilitySubmission, validPlrId).futureValue
        repository.insert(nilSubmission, validPlrId, chargeReference = Some(chargeReference)).futureValue

        repository.findByPillar2Id(validPlrId).futureValue.isDefined shouldBe true

        repository.deleteByPillar2Id(validPlrId).futureValue         shouldBe true
        repository.findByPillar2Id(validPlrId).futureValue.isDefined shouldBe false
      }

      "return true when attempting to delete non-existent pillar2Id" in {
        val deleteResult = repository.deleteByPillar2Id("NONEXISTENT").futureValue
        deleteResult shouldBe true
      }
    }
  }
}
