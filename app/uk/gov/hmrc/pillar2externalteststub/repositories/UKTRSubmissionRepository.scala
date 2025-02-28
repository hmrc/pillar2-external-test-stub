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
import play.api.Logging
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRSubmission}

import java.time.{Instant, LocalDate}
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UKTRSubmissionRepository @Inject() (config: AppConfig, mongoComponent: MongoComponent)(implicit ec: ExecutionContext)
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
    )
    with Logging {

  private def byPillar2Id(pillar2Id: String) = Filters.eq("pillar2Id", pillar2Id)

  def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Boolean] = {
    val document = UKTRMongoSubmission(
      _id = new ObjectId(),
      pillar2Id = pillar2Id,
      isAmendment = isAmendment,
      data = submission,
      submittedAt = Instant.now()
    )

    collection
      .insertOne(document)
      .toFuture()
      .map(_ => true)
      .recoverWith { case e: Exception =>
        logger.error(s"Failed to ${if (isAmendment) "amend" else "create"} UKTR", e)
        Future.failed(DatabaseError(s"Failed to ${if (isAmendment) "amend" else "create"} UKTR - ${e.getMessage}"))
      }
  }

  def update(submission: UKTRSubmission, pillar2Id: String): Future[Either[DetailedErrorResponse, Boolean]] =
    findByPillar2Id(pillar2Id).flatMap { maybeSubmission =>
      maybeSubmission match {
        case Some(_) =>
          insert(submission, pillar2Id, isAmendment = true)
            .map(Right(_))
            .recover { case _: DatabaseError =>
              logger.warn(s"Database error when updating submission for $pillar2Id - returning error response")
              Left(DetailedErrorResponse(UKTRDetailedError.RequestCouldNotBeProcessed))
            }
        case None =>
          Future.successful(Left(DetailedErrorResponse(UKTRDetailedError.RequestCouldNotBeProcessed)))
      }
    }

  def findByPillar2Id(pillar2Id: String): Future[Option[UKTRMongoSubmission]] =
    collection
      .find(byPillar2Id(pillar2Id))
      .sort(Sorts.descending("submittedAt"))
      .headOption()
      .recover {
        case e: DatabaseError =>
          logger.warn(s"Database error when finding submission for $pillar2Id: ${e.getMessage}")
          None
        case e: Exception =>
          logger.warn(s"Database error when finding submission for $pillar2Id: ${e.getMessage}")
          None
      }

  def isDuplicateSubmission(pillar2Id: String, accountingPeriodFrom: LocalDate, accountingPeriodTo: LocalDate): Future[Boolean] =
    collection
      .find(
        Filters.and(
          Filters.eq("pillar2Id", pillar2Id),
          Filters.eq("data.accountingPeriodFrom", accountingPeriodFrom.toString),
          Filters.eq("data.accountingPeriodTo", accountingPeriodTo.toString)
        )
      )
      .headOption()
      .map(_.isDefined)
      .recoverWith { case e: Exception =>
        logger.error(s"Failed to check for duplicate submission", e)
        Future.failed(DatabaseError(s"Failed to check for duplicate submission - ${e.getMessage}"))
      }
}
