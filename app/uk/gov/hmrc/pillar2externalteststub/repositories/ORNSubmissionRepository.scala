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

import org.bson.types.ObjectId
import org.mongodb.scala.model._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.orn.mongo.ORNSubmission

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ORNSubmissionRepository @Inject() (
  mongoComponent: MongoComponent,
  config:         AppConfig
)(implicit ec:    ExecutionContext)
    extends PlayMongoRepository[ORNSubmission](
      collectionName = "orn-submissions",
      mongoComponent = mongoComponent,
      domainFormat = ORNSubmission.mongoFormat,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("_id"),
          IndexOptions()
            .name("idIndex")
        ),
        IndexModel(
          Indexes.ascending("pillar2Id"),
          IndexOptions().name("pillar2IdIndex")
        ),
        IndexModel(
          Indexes.compoundIndex(
            Indexes.ascending("pillar2Id"),
            Indexes.ascending("accountingPeriodFrom"),
            Indexes.ascending("accountingPeriodTo")
          ),
          IndexOptions().name("pillar2Id_accountingPeriod_idx")
        ),
        IndexModel(
          Indexes.ascending("submittedAt"),
          IndexOptions()
            .name("submittedAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    ) {

  def insert(pillar2Id: String, submission: ORNRequest): Future[ObjectId] = {
    val document = ORNSubmission.fromRequest(pillar2Id, submission)

    collection
      .insertOne(document)
      .toFuture()
      .map(_ => document._id)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to save ORN submission: ${e.getMessage}"))
      }
  }

  def findByPillar2Id(pillar2Id: String): Future[Seq[ORNSubmission]] =
    collection
      .find(Filters.equal("pillar2Id", pillar2Id))
      .toFuture()
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to retrieve ORN submissions: ${e.getMessage}"))
      }

  def findByPillar2IdAndAccountingPeriod(
    pillar2Id:            String,
    accountingPeriodFrom: LocalDate,
    accountingPeriodTo:   LocalDate
  ): Future[Option[ORNSubmission]] =
    collection
      .find(
        Filters.and(
          Filters.equal("pillar2Id", pillar2Id),
          Filters.equal("accountingPeriodFrom", accountingPeriodFrom.toString),
          Filters.equal("accountingPeriodTo", accountingPeriodTo.toString)
        )
      )
      .sort(Sorts.descending("submittedAt"))
      .headOption()
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to retrieve ORN submission: ${e.getMessage}"))
      }

  def deleteByPillar2Id(pillar2Id: String): Future[Boolean] =
    collection
      .deleteMany(Filters.equal("pillar2Id", pillar2Id))
      .toFuture()
      .map(_ => true)
      .recoverWith { case e: Exception => Future.failed(DatabaseError(s"Failed to delete ORN submission: ${e.getMessage}")) }
}
