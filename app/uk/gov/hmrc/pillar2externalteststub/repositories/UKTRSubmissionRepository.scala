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

import cats.implicits.toTraverseOps
import org.mongodb.scala.bson.ObjectId
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Indexes.descending
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRSubmission}

import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UKTRSubmissionRepository @Inject() (config: AppConfig, mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[JsObject](
      collectionName = "uktr-submissions",
      mongoComponent = mongoComponent,
      domainFormat = implicitly[Format[JsObject]],
      indexes = Seq(
        IndexModel(
          Indexes.ascending("createdAt"),
          IndexOptions()
            .name("createdAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    ) {

  def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] = {
    val document = Json.obj(
      "_id"         -> new ObjectId().toString,
      "pillar2Id"   -> pillar2Id,
      "isAmendment" -> isAmendment,
      "data"        -> Json.toJson(submission).as[JsObject],
      "createdAt"   -> Json.obj("$date" -> Instant.now.toEpochMilli)
    )

    collection
      .insertOne(document)
      .toFuture()
      .map(_ => true)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to ${if (isAmendment) "amend" else "create"} UKTR - ${e.getMessage}"))
      }
  }

  def update(submission: UKTRSubmission, pillar2Id: String): Future[Either[DetailedErrorResponse, Boolean]] =
    findByPillar2Id(pillar2Id).flatMap(
      _.toRight(RequestCouldNotBeProcessed)
        .traverse(_ => insert(submission, pillar2Id, isAmendment = true))
    )

  def findByPillar2Id(pillar2Id: String): Future[Option[JsObject]] =
    collection
      .find(equal("pillar2Id", pillar2Id))
      .sort(descending("createdAt"))
      .headOption()
}
