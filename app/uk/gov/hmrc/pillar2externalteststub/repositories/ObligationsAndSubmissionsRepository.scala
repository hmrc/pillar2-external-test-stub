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

import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.{ascending, compoundIndex}
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.ObligationsAndSubmissionsMongoSubmission

import java.time.LocalDate
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ObligationsAndSubmissionsRepository @Inject() (
  mongoComponent: MongoComponent,
  config:         AppConfig
)(implicit ec:    ExecutionContext)
    extends PlayMongoRepository[ObligationsAndSubmissionsMongoSubmission](
      collectionName = "obligations-and-submissions",
      mongoComponent = mongoComponent,
      domainFormat = ObligationsAndSubmissionsMongoSubmission.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("_id"),
          IndexOptions()
            .name("idIndex")
        ),
        IndexModel(
          compoundIndex(
            ascending("pillar2Id"),
            ascending("submissionId")
          ),
          IndexOptions()
            .name("pillar2Id_oas_Index")
        ),
        IndexModel(
          Indexes.ascending("submittedAt"),
          IndexOptions()
            .name("submittedAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    )
    with Logging {

  def insert(submission: BaseSubmission, pillar2Id: String, id: ObjectId, isAmendment: Boolean = false): Future[Boolean] =
    collection
      .insertOne(ObligationsAndSubmissionsMongoSubmission.fromRequest(pillar2Id, submission, id, isAmendment))
      .toFuture()
      .map { _ =>
        logger.info("Successfully saved entry to oas collection.")
        true
      }
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to insert submission to oas collection: ${e.getMessage}"))
      }

  def deleteByPillar2Id(pillar2Id: String): Future[Boolean] =
    collection
      .deleteMany(equal("pillar2Id", pillar2Id))
      .toFuture()
      .map(_.wasAcknowledged())

  def findByPillar2Id(pillar2Id: String, from: LocalDate, to: LocalDate): Future[Seq[ObligationsAndSubmissionsMongoSubmission]] = {
    val filter =
      and(
        equal("pillar2Id", pillar2Id),
        nor(gt("accountingPeriod.startDate", to), lte("accountingPeriod.endDate", from))
      )

    collection
      .find(filter)
      .toFuture()
      .recoverWith { case e: Exception => Future.failed(DatabaseError(s"Failed to retrieve matching records: ${e.getMessage}")) }
  }
}
