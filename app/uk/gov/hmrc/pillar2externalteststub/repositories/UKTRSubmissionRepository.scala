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

import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.Sorts.descending
import org.mongodb.scala.model.{IndexModel, IndexOptions, Indexes}
import play.api.Logging
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.InvalidSubmission
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
import uk.gov.hmrc.pillar2externalteststub.models.uktr.{DetailedErrorResponse, UKTRDetailedError, UKTRSubmission}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UKTRSubmissionRepository @Inject() (
  config:                 AppConfig,
  mongoComponent:         MongoComponent,
  organisationRepository: OrganisationRepository
)(implicit ec:            ExecutionContext)
    extends PlayMongoRepository[JsObject](
      collectionName = "uktr-submissions",
      mongoComponent = mongoComponent,
      domainFormat = implicitly[Format[JsObject]],
      indexes = Seq(
        IndexModel(
          Indexes.ascending("pillar2Id"),
          IndexOptions()
            .name("uktr_submissions_pillar2Id_idx")
            .sparse(true)
            .background(true)
        ),
        IndexModel(
          Indexes.ascending("createdAt"),
          IndexOptions()
            .name("createdAtTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
            .background(true)
        )
      )
    )
    with Logging {

  // Define our own implicit conversion for java.util.Date that uses MongoDB extended JSON format
  private implicit val dateWrites: Writes[java.util.Date] = new Writes[java.util.Date] {
    def writes(d: java.util.Date): JsValue = Json.obj(
      "$date" -> d.getTime
    )
  }

  // Ensure indexes are created with error handling
  collection.createIndexes(indexes).toFuture().recover[Seq[String]] { case e: Exception =>
    logger.warn(s"Failed to ensure indexes: ${e.getMessage}")
    Seq.empty[String]
  }

  private def validateSubmission(submission: UKTRSubmission, organisation: TestOrganisationWithId): Either[DetailedErrorResponse, Unit] = {
    val submissionPeriod = (submission.accountingPeriodFrom, submission.accountingPeriodTo)
    val orgPeriod        = (organisation.organisation.accountingPeriod.startDate, organisation.organisation.accountingPeriod.endDate)

    if (submissionPeriod != orgPeriod) {
      Left(InvalidSubmission("Accounting period does not match registered period"))
    } else if (organisation.organisation.orgDetails.domesticOnly && submission.obligationMTT) {
      Left(
        DetailedErrorResponse(
          UKTRDetailedError(
            processingDate = java.time.Instant.now().toString(),
            code = REQUEST_COULD_NOT_BE_PROCESSED_003,
            text = "Domestic only groups cannot have MTT values"
          )
        )
      )
    } else {
      Right(())
    }
  }

  def insert(submission: UKTRSubmission, pillar2Id: String, isAmendment: Boolean = false): Future[Either[DetailedErrorResponse, Boolean]] =
    organisationRepository.findByPillar2Id(pillar2Id).flatMap {
      case Some(org) =>
        validateSubmission(submission, org) match {
          case Right(_) =>
            if (isAmendment) {
              update(submission, pillar2Id)
            } else {
              val now = java.time.Instant.now()
              val doc = Json.toJson(submission).as[JsObject] ++ Json.obj("createdAt" -> toDate(now), "pillar2Id" -> pillar2Id)
              collection.find(equal("pillar2Id", pillar2Id)).headOption().flatMap {
                case Some(_) => Future.successful(Left(UKTRDetailedError.TaxObligationFulfilled))
                case None =>
                  collection.insertOne(doc).toFuture().map(_ => Right(true)).recover {
                    case e: Exception if e.getMessage.toLowerCase.contains("duplicate") =>
                      Left(UKTRDetailedError.TaxObligationFulfilled)
                    case e: Exception =>
                      Left(InvalidSubmission(s"Failed to insert submission: ${e.getMessage}"))
                  }
              }
            }
          case Left(error) => Future.successful(Left(error))
        }
      case None =>
        Future.successful(Left(InvalidSubmission("Organisation not found")))
    }

  def findByPillar2Id(pillar2Id: String): Future[Either[DetailedErrorResponse, Option[JsObject]]] =
    collection
      .find(equal("pillar2Id", pillar2Id))
      .sort(descending("createdAt"))
      .headOption()
      .map(docOpt => Right(docOpt.map(doc => doc.as[JsObject] - "_id" - "createdAt" - "pillar2Id")))
      .recover { case e: Exception =>
        Left(InvalidSubmission(s"Failed to find submission: ${e.getMessage}"))
      }

  def update(submission: UKTRSubmission, pillar2Id: String): Future[Either[DetailedErrorResponse, Boolean]] =
    organisationRepository.findByPillar2Id(pillar2Id).flatMap {
      case Some(org) =>
        validateSubmission(submission, org) match {
          case Right(_) =>
            collection.find(equal("pillar2Id", pillar2Id)).headOption().flatMap { existingDocOpt =>
              val createdAt =
                existingDocOpt.flatMap(doc => (doc \ "createdAt").asOpt[java.util.Date].map(_.toInstant)).getOrElse(java.time.Instant.now())
              val newDoc = Json.toJson(submission).as[JsObject] ++ Json.obj("createdAt" -> toDate(createdAt), "pillar2Id" -> pillar2Id)
              collection
                .replaceOne(
                  filter = equal("pillar2Id", pillar2Id),
                  replacement = newDoc
                )
                .toFuture()
                .map { result =>
                  if (result.getMatchedCount == 0) Left(UKTRDetailedError.RequestCouldNotBeProcessed) else Right(true)
                }
                .recover { case e: Exception =>
                  Left(InvalidSubmission(s"Failed to update submission: ${e.getMessage}"))
                }
            }
          case Left(error) => Future.successful(Left(error))
        }
      case None =>
        Future.successful(Left(InvalidSubmission("Organisation not found")))
    }

  private def toDate(instant: java.time.Instant): java.util.Date = {
    val d   = java.util.Date.from(instant)
    val fmt = implicitly[Writes[java.util.Date]]
    // Force usage of the implicit Writes from MongoJavatimeFormats to ensure correct extended JSON formatting
    fmt.writes(d)
    d
  }
}
