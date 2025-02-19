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

package uk.gov.hmrc.pillar2externalteststub.helpers

import org.scalatest.{BeforeAndAfterEach, Suite}
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logging
import uk.gov.hmrc.pillar2externalteststub.repositories.{OrganisationRepository, UKTRSubmissionRepository}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait MongoCleanupSupport extends BeforeAndAfterEach with Logging { this: Suite with GuiceOneAppPerSuite =>
  override def beforeEach(): Unit = {
    super.beforeEach()
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global
    val uktrRepo = app.injector.instanceOf[UKTRSubmissionRepository]
    val orgRepo = app.injector.instanceOf[OrganisationRepository]
    
    // Helper function to safely execute MongoDB operations
    def safeMongoOp[T](operation: => scala.concurrent.Awaitable[T], errorMsg: String)(implicit ec: scala.concurrent.ExecutionContext): Unit = {
      Try(Await.result(operation, 5.seconds)) match {
        case Success(_) => ()
        case Failure(e) => logger.warn(s"$errorMsg: ${e.getMessage}")
      }
    }
    
    // Clean up UKTR submissions collection
    safeMongoOp(
      uktrRepo.collection.drop().toFuture(),
      "Failed to drop UKTR submissions collection"
    )
    
    safeMongoOp(
      uktrRepo.collection.dropIndexes().toFuture(),
      "Failed to drop UKTR indexes"
    )
    
    // Clean up Organisation collection
    safeMongoOp(
      orgRepo.collection.drop().toFuture(),
      "Failed to drop Organisation collection"
    )
    
    safeMongoOp(
      orgRepo.collection.dropIndexes().toFuture(),
      "Failed to drop Organisation indexes"
    )
    
    // Recreate collections and indexes
    safeMongoOp(
      uktrRepo.collection.createIndexes(uktrRepo.indexes).toFuture().map { indexNames =>
        logger.info(s"Created UKTR indexes: ${indexNames.mkString(", ")}")
      },
      "Failed to create UKTR indexes"
    )
    
    safeMongoOp(
      orgRepo.collection.createIndexes(orgRepo.indexes).toFuture().map { indexNames =>
        logger.info(s"Created Organisation indexes: ${indexNames.mkString(", ")}")
      },
      "Failed to create Organisation indexes"
    )
  }
} 