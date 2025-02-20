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
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationRepository @Inject() (
  mongoComponent: uk.gov.hmrc.mongo.MongoComponent,
  appConfig:      AppConfig
)(implicit ec:    ExecutionContext)
    extends PlayMongoRepository[TestOrganisationWithId](
      collectionName = "organisation",
      mongoComponent = mongoComponent,
      domainFormat = TestOrganisationWithId.format,
      indexes = Seq(
        IndexModel(
          Indexes.ascending("pillar2Id"),
          IndexOptions().name("pillar2IdIndex").unique(true)
        ),
        IndexModel(
          Indexes.ascending("organisation.lastUpdated"),
          IndexOptions()
            .name("lastUpdatedTTL")
            .expireAfter(appConfig.defaultDataExpireInDays, TimeUnit.DAYS)
        )
      )
    ) {

  private def byPillar2Id(pillar2Id: String): Bson = Filters.equal("pillar2Id", pillar2Id)

  def insert(details: TestOrganisationWithId): Future[Boolean] =
    collection.insertOne(details).toFuture().map(_ => true)

  def findByPillar2Id(pillar2Id: String): Future[Option[TestOrganisationWithId]] =
    collection.find(byPillar2Id(pillar2Id)).headOption()

  def update(details: TestOrganisationWithId): Future[Boolean] =
    collection
      .replaceOne(
        filter = byPillar2Id(details.pillar2Id),
        replacement = details,
        options = ReplaceOptions().upsert(true)
      )
      .toFuture()
      .map(result => result.getMatchedCount > 0 || result.getUpsertedId != null)

  def delete(pillar2Id: String): Future[Boolean] =
    collection
      .deleteOne(byPillar2Id(pillar2Id))
      .toFuture()
      .map(_ => true)
      .recover { case e: Exception =>
        throw new Exception(s"Failed to delete organisation: ${e.getMessage}")
      }
}
