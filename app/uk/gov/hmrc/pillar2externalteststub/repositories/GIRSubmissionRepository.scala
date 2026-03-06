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
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.models.gir.mongo.GIRSubmission

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class GIRSubmissionRepository @Inject() (
  mongoComponent: MongoComponent,
  config:         AppConfig
)(using ec:       ExecutionContext)
    extends PlayMongoRepository[GIRSubmission](
      collectionName = "gir-submissions",
      mongoComponent = mongoComponent,
      domainFormat = GIRSubmission.mongoFormat,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("_id"),
          IndexOptions().name("idIndex")
        ),
        IndexModel(
          Indexes.ascending("pillar2Id"),
          IndexOptions().name("pillar2IdIndex")
        ),
        IndexModel(
          Indexes.ascending("submittedAt"),
          IndexOptions()
            .name("submittedAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    ) {

  def insert(pillar2Id: String, submission: GIRRequest): Future[ObjectId] = {
    val document = GIRSubmission.fromRequest(pillar2Id, submission)

    collection
      .insertOne(document)
      .toFuture()
      .map(_ => document._id)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to save GIR submission: ${e.getMessage}"))
      }
  }

  def findByPillar2Id(pillar2Id: String): Future[Seq[GIRSubmission]] =
    collection
      .find(Filters.equal("pillar2Id", pillar2Id))
      .toFuture()
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to retrieve GIR submissions: ${e.getMessage}"))
      }

  def findByPillar2IdAndAccountingPeriod(pillar2Id: String, from: LocalDate, to: LocalDate): Future[Option[GIRSubmission]] =
    collection
      .find(
        Filters.and(
          Filters.equal("pillar2Id", pillar2Id),
          Filters.equal("accountingPeriodFrom", from.toString),
          Filters.equal("accountingPeriodTo", to.toString)
        )
      )
      .sort(Sorts.descending("submittedAt"))
      .headOption()
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to retrieve GIR submission: ${e.getMessage}"))
      }

  def deleteByPillar2Id(pillar2Id: String): Future[Boolean] =
    collection
      .deleteMany(Filters.equal("pillar2Id", pillar2Id))
      .toFuture()
      .map(_ => true)
      .recoverWith { case e: Exception => Future.failed(DatabaseError(s"Failed to delete GIR submission: ${e.getMessage}")) }
}
