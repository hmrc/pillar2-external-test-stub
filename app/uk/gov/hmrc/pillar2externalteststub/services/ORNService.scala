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

package uk.gov.hmrc.pillar2externalteststub.services

import play.api.Logging
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.TaxObligationAlreadyFulfilled
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.models.orn.mongo.ORNSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.ORNSubmissionRepository

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ORNService @Inject() (
  repository:  ORNSubmissionRepository
)(implicit ec: ExecutionContext)
    extends Logging {

  def submitORN(pillar2Id: String, request: ORNRequest): Future[Boolean] =
    repository.findByPillar2IdAndAccountingPeriod(pillar2Id, request.accountingPeriodFrom, request.accountingPeriodTo).flatMap {
      case Some(_) =>
        Future.failed(TaxObligationAlreadyFulfilled)
      case None =>
        repository.insert(pillar2Id, request)
    }

  def amendORN(pillar2Id: String, request: ORNRequest): Future[Boolean] = {
    logger.info(s"Amending ORN for pillar2Id: $pillar2Id")
    repository.findByPillar2IdAndAccountingPeriod(pillar2Id, request.accountingPeriodFrom, request.accountingPeriodTo).flatMap {
      case Some(_) =>
        repository.insert(pillar2Id, request)
      case None =>
        Future.failed(RequestCouldNotBeProcessed)
    }
  }

  def getORN(pillar2Id: String, accountingPeriodFrom: LocalDate, accountingPeriodTo: LocalDate): Future[Option[ORNSubmission]] =
    repository
      .findByPillar2IdAndAccountingPeriod(pillar2Id, accountingPeriodFrom, accountingPeriodTo)
}
