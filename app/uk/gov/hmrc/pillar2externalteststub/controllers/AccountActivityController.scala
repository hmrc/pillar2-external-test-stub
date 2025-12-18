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

package uk.gov.hmrc.pillar2externalteststub.controllers

import play.api.Logging
import play.api.mvc.*
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{NoDataFound, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.{HIPBadRequest, OrganisationNotFound, TestDataNotFound}
import uk.gov.hmrc.pillar2externalteststub.services.{AccountActivityService, OrganisationService}
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try

@Singleton
class AccountActivityController @Inject() (
  cc:                     ControllerComponents,
  authFilter:             AuthActionFilter,
  organisationService:    OrganisationService,
  accountActivityService: AccountActivityService
)(using ec:               ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def get(fromDate: String, toDate: String): Action[AnyContent] = (Action andThen authFilter).async { request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")).flatMap { pillar2Id =>
      (for {
        _ <- if request.headers.get("X-Message-Type").contains("ACCOUNT_ACTIVITY") then Future.unit
             else Future.failed(HIPBadRequest())
        from         <- Future.fromTry(Try(LocalDate.parse(fromDate)))
        to           <- Future.fromTry(Try(LocalDate.parse(toDate)))
        _            <- if from.isAfter(to) then Future.failed(RequestCouldNotBeProcessed) else Future.unit
        orgWithId    <- organisationService.getOrganisation(pillar2Id)
        responseJson <- accountActivityService.getAccountActivity(orgWithId)
      } yield Ok(responseJson))
        .recoverWith {
          case _: OrganisationNotFound =>
            logger.warn(s"Organisation not found pillar2Id: $pillar2Id")
            Future.failed(NoDataFound)
          case _: TestDataNotFound =>
            logger.warn(s"Test data missing for pillar2Id: $pillar2Id")
            Future.failed(TestDataNotFound(pillar2Id))
          case e: DateTimeParseException =>
            logger.error(s"Invalid date format: ${e.getMessage}")
            Future.failed(RequestCouldNotBeProcessed)
        }
    }
  }
}
