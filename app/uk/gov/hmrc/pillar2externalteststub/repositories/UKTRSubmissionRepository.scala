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
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.descending
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.generateChargeReference
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{Liability, UKTRSubmission}

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UKTRSubmissionRepository @Inject() (config: AppConfig, mongoComponent: MongoComponent)(using ec: ExecutionContext)
    extends PlayMongoRepository[UKTRMongoSubmission](
      collectionName = "uktr-submissions",
      mongoComponent = mongoComponent,
      domainFormat = UKTRMongoSubmission.format,
      indexes = Seq(
        IndexModel(
          Indexes.compoundIndex(
            Indexes.ascending("pillar2Id"),
            Indexes.descending("submittedAt")
          ),
          IndexOptions().name("pillar2IdIndex")
        ),
        IndexModel(
          Indexes.ascending("submittedAt"),
          IndexOptions()
            .name("submittedAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      ),
      replaceIndexes = true
    ) {

  def insert(submission: UKTRSubmission, pillar2Id: String, chargeReference: Option[String] = None): Future[ObjectId] = {
    val document = UKTRMongoSubmission(
      _id = new ObjectId(),
      pillar2Id = pillar2Id,
      chargeReference = chargeReference,
      data = submission,
      submittedAt = Instant.now()
    )

    collection
      .insertOne(document)
      .toFuture()
      .map(_ => document._id)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to ${if (chargeReference.isEmpty) "amend" else "create"} UKTR - ${e.getMessage}"))
      }
  }

  def update(submission: UKTRSubmission, pillar2Id: String): Future[(ObjectId, Option[String])] =
    findByPillar2Id(pillar2Id)
      .map(_.head)
      .recoverWith { case _: Exception => throw RequestCouldNotBeProcessed }
      .flatMap { parentSubmission =>
        val chargeReference: Option[String] =
          if (submission.liabilities.isInstanceOf[Liability] && parentSubmission.chargeReference.isEmpty)
            Some(generateChargeReference())
          else parentSubmission.chargeReference
        insert(submission, pillar2Id, chargeReference).map(objectId => (objectId, chargeReference))
      }

  def findByPillar2Id(pillar2Id: String): Future[Option[UKTRMongoSubmission]] =
    collection
      .find(equal("pillar2Id", pillar2Id))
      .sort(descending("submittedAt"))
      .headOption()

  def deleteByPillar2Id(pillar2Id: String): Future[Boolean] =
    collection
      .deleteMany(equal("pillar2Id", pillar2Id))
      .toFuture()
      .map(_.wasAcknowledged())
}
