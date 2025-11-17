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
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.*
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.btn.mongo.BTNSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BTNSubmissionRepository @Inject() (
  mongoComponent: MongoComponent,
  config:         AppConfig
)(using ec:       ExecutionContext)
    extends PlayMongoRepository[BTNSubmission](
      collectionName = "btn-submissions",
      mongoComponent = mongoComponent,
      domainFormat = BTNSubmission.mongoFormat,
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
          Indexes.ascending("submittedAt"),
          IndexOptions()
            .name("submittedAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    ) {

  def insert(pillar2Id: String, submission: BTNRequest): Future[ObjectId] = {
    val document = BTNSubmission.fromRequest(pillar2Id, submission)

    collection
      .insertOne(document)
      .toFuture()
      .map(_ => document._id)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to save BTN submission: ${e.getMessage}"))
      }
  }

  def findByPillar2Id(pillar2Id: String): Future[Seq[BTNSubmission]] =
    collection
      .find(Filters.equal("pillar2Id", pillar2Id))
      .toFuture()
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to retrieve BTN submissions: ${e.getMessage}"))
      }

  def deleteByPillar2Id(pillar2Id: String): Future[Boolean] =
    collection
      .deleteMany(equal("pillar2Id", pillar2Id))
      .toFuture()
      .map(_.wasAcknowledged())
}
