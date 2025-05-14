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

import org.bson.conversions.Bson
import org.mongodb.scala.model._
import play.api.libs.json._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{TestOrganisation, TestOrganisationWithId}

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationRepository @Inject() (
  mongoComponent: MongoComponent,
  config:         AppConfig,
  btnRepository:  BTNSubmissionRepository,
  uktrRepository: UKTRSubmissionRepository,
  oasRepository:  ObligationsAndSubmissionsRepository,
  ornRepository:  ORNSubmissionRepository,
  girRepository:  GIRSubmissionRepository
)(implicit ec:    ExecutionContext)
    extends PlayMongoRepository[TestOrganisationWithId](
      collectionName = "organisation",
      mongoComponent = mongoComponent,
      domainFormat = new Format[TestOrganisationWithId] {
        def reads(json: JsValue): JsResult[TestOrganisationWithId] = for {
          pillar2Id    <- (json \ "pillar2Id").validate[String]
          organisation <- (json \ "organisation").validate[TestOrganisation](TestOrganisation.mongoFormat)
        } yield TestOrganisationWithId(pillar2Id, organisation)

        def writes(o: TestOrganisationWithId): JsValue = Json.obj(
          "pillar2Id"    -> o.pillar2Id,
          "organisation" -> Json.toJson(o.organisation)(TestOrganisation.mongoFormat)
        )
      },
      indexes = Seq(
        IndexModel(
          Indexes.ascending("pillar2Id"),
          IndexOptions().name("pillar2IdIndex").unique(true)
        ),
        IndexModel(
          Indexes.ascending("organisation.lastUpdated"),
          IndexOptions()
            .name("lastUpdatedTTL")
            .expireAfter(config.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    ) {

  private def byPillar2Id(pillar2Id: String): Bson = Filters.equal("pillar2Id", pillar2Id)

  def insert(details: TestOrganisationWithId): Future[Boolean] =
    collection
      .insertOne(details)
      .toFuture()
      .map(_ => true)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to create organisation: ${e.getMessage}"))
      }

  def findByPillar2Id(pillar2Id: String): Future[Option[TestOrganisationWithId]] =
    collection
      .find(byPillar2Id(pillar2Id))
      .headOption()
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to find organisation: ${e.getMessage}"))
      }

  def update(details: TestOrganisationWithId): Future[Boolean] =
    collection
      .replaceOne(
        filter = byPillar2Id(details.pillar2Id),
        replacement = details,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(_ => true)
      .recoverWith { case e: Exception =>
        Future.failed(DatabaseError(s"Failed to update organisation: ${e.getMessage}"))
      }

  def delete(pillar2Id: String): Future[Boolean] = {
    for {
      _         <- uktrRepository.deleteByPillar2Id(pillar2Id)
      _         <- btnRepository.deleteByPillar2Id(pillar2Id)
      _         <- ornRepository.deleteByPillar2Id(pillar2Id)
      _         <- girRepository.deleteByPillar2Id(pillar2Id)
      _         <- oasRepository.deleteByPillar2Id(pillar2Id)
      deleteOrg <- collection.deleteOne(byPillar2Id(pillar2Id)).toFuture()
    } yield deleteOrg.wasAcknowledged()
  }.recoverWith { case e: Exception => Future.failed(DatabaseError(s"Failed to delete organisation and associated submissions: ${e.getMessage}")) }
}
