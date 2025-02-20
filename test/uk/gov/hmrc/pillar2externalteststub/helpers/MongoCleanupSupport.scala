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

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

trait MongoCleanupSupport extends BeforeAndAfterEach with Logging { this: Suite with GuiceOneAppPerSuite =>

  // Helper function to safely execute MongoDB operations with retries
  private def safeMongoOp[T](operation: => Future[T], errorMsg: String, maxRetries: Int = 3)(implicit
    ec:                                 scala.concurrent.ExecutionContext
  ): T = {
    def retry(remainingRetries: Int, delay: FiniteDuration = 500.milliseconds): T =
      Try(Await.result(operation, 5.seconds)) match {
        case Success(result) =>
          logger.info(s"Successfully completed operation: $errorMsg")
          result
        case Failure(e) if remainingRetries > 0 =>
          logger.warn(s"$errorMsg: ${e.getMessage}, retrying in ${delay.toMillis}ms... (${remainingRetries - 1} attempts remaining)")
          Thread.sleep(delay.toMillis)
          retry(remainingRetries - 1, delay * 2)
        case Failure(e) =>
          logger.error(s"$errorMsg after $maxRetries attempts: ${e.getMessage}")
          throw new RuntimeException(s"Failed to complete MongoDB operation after $maxRetries attempts: ${e.getMessage}", e)
      }
    retry(maxRetries)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.global

    try {
      logger.info("Starting database cleanup...")

      // Drop the entire database to ensure a clean state
      val database = app.injector.instanceOf[uk.gov.hmrc.mongo.MongoComponent].database
      safeMongoOp(database.drop().toFuture(), "Dropping test database")

      logger.info("Database cleanup completed successfully")
    } catch {
      case e: Exception =>
        val errorMsg = s"Failed to complete database cleanup: ${e.getMessage}"
        logger.error(errorMsg, e)
        throw new RuntimeException(errorMsg, e)
    }
  }
}
