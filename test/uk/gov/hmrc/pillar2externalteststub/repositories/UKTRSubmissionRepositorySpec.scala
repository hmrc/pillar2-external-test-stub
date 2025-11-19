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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.{MongoCollection, SingleObservableFuture}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Configuration}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission

import scala.concurrent.{ExecutionContext, Future}

class UKTRSubmissionRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[UKTRMongoSubmission]
    with IntegrationPatience
    with ScalaFutures
    with UKTRDataFixture
    with MockitoSugar {

  val config = new AppConfig(Configuration.from(Map("appName" -> "pillar2-external-test-stub", "defaultDataExpireInDays" -> 28)))
  val app: Application = GuiceApplicationBuilder()
    .overrides(bind[MongoComponent].toInstance(mongoComponent))
    .build()
  val repository: UKTRSubmissionRepository =
    new UKTRSubmissionRepository(config, mongoComponent)(using app.injector.instanceOf[ExecutionContext])

  def submitLiabilityUktr(chargeReference: Option[String] = None): Future[ObjectId] =
    repository.insert(liabilitySubmission, validPlrId, chargeReference)
  def submitNilUktr:                                     Future[ObjectId]                    = repository.insert(nilSubmission, validPlrId)
  def amendWithLiabilityUktr:                            Future[(ObjectId, Option[String])]  = repository.update(liabilitySubmission, validPlrId)
  def amendWithNilUktr:                                  Future[(ObjectId, Option[String])]  = repository.update(nilSubmission, validPlrId)
  def deleteSubmissions(pillar2Id: String = validPlrId): Future[Boolean]                     = repository.deleteByPillar2Id(pillar2Id)
  def findSubmissions(pillar2Id: String = validPlrId):   Future[Option[UKTRMongoSubmission]] = repository.findByPillar2Id(pillar2Id)

  "UKTRSubmissionRepository" when {
    "handling valid submissions" should {
      "successfully insert a liability return" in {
        submitLiabilityUktr().futureValue shouldBe a[ObjectId]
      }

      "successfully insert a nil return" in {
        submitNilUktr.futureValue shouldBe a[ObjectId]
      }

      "successfully handle amendments" should {
        "Liability -> Liability" in {
          submitLiabilityUktr(Some(chargeReference)).futureValue

          val amendedSubmission = amendWithLiabilityUktr.futureValue
          amendedSubmission._1.isInstanceOf[ObjectId] shouldBe true
          amendedSubmission._2.get                    shouldBe chargeReference
        }

        "Liability -> Nil" in {
          submitLiabilityUktr().futureValue

          val amendedSubmission = amendWithNilUktr.futureValue
          amendedSubmission._1.isInstanceOf[ObjectId] shouldBe true
        }

        "Nil -> Liability" in {
          submitNilUktr.futureValue

          val amendedSubmission = amendWithLiabilityUktr.futureValue
          amendedSubmission._1.isInstanceOf[ObjectId] shouldBe true
          amendedSubmission._2.isDefined              shouldBe true
        }

        "Nil -> Nil" in {
          submitNilUktr.futureValue

          val amendedSubmission = amendWithNilUktr.futureValue
          amendedSubmission._1.isInstanceOf[ObjectId] shouldBe true
          amendedSubmission._2                        shouldBe None
        }
      }
    }

    "handling invalid submissions" should {
      "fail when attempting to update non-existent submission" in {
        amendWithLiabilityUktr.failed.futureValue shouldBe a[RequestCouldNotBeProcessed.type]
      }
    }

    "handling deletions" should {
      "successfully delete all submissions for a given pillar2Id" in {
        submitNilUktr.futureValue
        submitLiabilityUktr(chargeReference = Some(chargeReference)).futureValue

        findSubmissions().futureValue.isDefined shouldBe true

        deleteSubmissions().futureValue         shouldBe true
        findSubmissions().futureValue.isDefined shouldBe false
      }

      "return true when attempting to delete non-existent pillar2Id" in {
        deleteSubmissions("NONEXISTENT").futureValue shouldBe true
      }
    }

    "handle database failures gracefully" should {
      "wrap insert failures in DatabaseError" in {
        val mockCollection = mock[MongoCollection[UKTRMongoSubmission]](withSettings().defaultAnswer(RETURNS_DEEP_STUBS))

        when(mockCollection.insertOne(any[UKTRMongoSubmission]).toFuture())
          .thenReturn(Future.failed(new RuntimeException("DB Write Failure")))

        val failingRepo = new UKTRSubmissionRepository(config, mongoComponent)(using ExecutionContext.global) {
          override lazy val collection: MongoCollection[UKTRMongoSubmission] = mockCollection
        }

        val result = failingRepo.insert(liabilitySubmission, validPlrId, Some("someChargeReference")).failed.futureValue

        result          shouldBe a[DatabaseError]
        result.getMessage should include("Failed to create UKTR - DB Write Failure")
      }
    }

  }
}
