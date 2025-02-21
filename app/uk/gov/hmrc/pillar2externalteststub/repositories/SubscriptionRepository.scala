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

import org.mongodb.scala.model.Filters
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.pillar2externalteststub.models.subscription._

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubscriptionRepository @Inject() (
  mongoComponent: MongoComponent
)(implicit ec: ExecutionContext)
    extends PlayMongoRepository[SubscriptionMongo](
      collectionName = "subscription",
      mongoComponent = mongoComponent,
      domainFormat = SubscriptionMongo.format,
      indexes = Seq()
    ) {

  def findByPLRReference(plrReference: String): Future[Option[Subscription]] =
    collection
      .find(Filters.eq("plrReference", plrReference))
      .headOption()
      .map(_.map(toSubscription))

  def insert(subscription: Subscription): Future[Boolean] = {
    val subscriptionMongo = toSubscriptionMongo(subscription)
    collection
      .insertOne(subscriptionMongo)
      .toFuture()
      .map(_ => true)
  }

  private def toSubscription(mongo: SubscriptionMongo): Subscription =
    Subscription(
      plrReference = mongo.plrReference,
      upeDetails = mongo.upeDetails,
      addressDetails = mongo.addressDetails,
      contactDetails = mongo.contactDetails,
      secondaryContactDetails = mongo.secondaryContactDetails,
      filingMemberDetails = mongo.filingMemberDetails,
      accountingPeriod = mongo.accountingPeriod,
      accountStatus = mongo.accountStatus
    )

  private def toSubscriptionMongo(subscription: Subscription): SubscriptionMongo =
    SubscriptionMongo(
      plrReference = subscription.plrReference,
      upeDetails = subscription.upeDetails,
      addressDetails = subscription.addressDetails,
      contactDetails = subscription.contactDetails,
      secondaryContactDetails = subscription.secondaryContactDetails,
      filingMemberDetails = subscription.filingMemberDetails,
      accountingPeriod = subscription.accountingPeriod,
      accountStatus = subscription.accountStatus
    )
} 