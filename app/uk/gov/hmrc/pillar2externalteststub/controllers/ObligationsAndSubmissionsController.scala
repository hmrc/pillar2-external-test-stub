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
import play.api.libs.json.Json
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.nowZonedDateTime
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{NoAssociatedDataFound, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.ObligationStatus.{Fulfilled, Open}
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.ObligationType.{GlobeInformationReturn, Pillar2TaxReturn}
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.SubmissionType._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions._
import uk.gov.hmrc.pillar2externalteststub.models.obligationsAndSubmissions.mongo.ObligationsAndSubmissionsMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.repositories.ObligationsAndSubmissionsRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.time.{LocalDate, ZoneOffset, ZonedDateTime}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

@Singleton
class ObligationsAndSubmissionsController @Inject() (
  authFilter:          AuthActionFilter,
  cc:                  ControllerComponents,
  organisationService: OrganisationService,
  oasRepository:       ObligationsAndSubmissionsRepository
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def getObligationsAndSubmissions(fromDate: String, toDate: String): Action[AnyContent] = (Action andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")).flatMap { pillar2Id =>
      (for {
        localFromDate <- Future.fromTry(Try(LocalDate.parse(fromDate)))
        localToDate   <- Future.fromTry(Try(LocalDate.parse(toDate)))
        testOrg <- organisationService.getOrganisation(pillar2Id).flatMap { org =>
                     if (
                       !localFromDate.isAfter(org.organisation.accountingPeriod.startDate) && !localToDate
                         .isBefore(org.organisation.accountingPeriod.endDate)
                     ) Future.successful(org)
                     else Future.failed(NoAssociatedDataFound)
                   }
        oasSubmissions <- oasRepository.findAllSubmissionsByPillar2Id(pillar2Id)
      } yield generateHistory(testOrg, oasSubmissions))
        .recoverWith {
          case _: OrganisationNotFound =>
            logger.warn(s"Organisation not found pillar2Id: $pillar2Id")
            Future.failed(NoAssociatedDataFound)
          case e: DateTimeParseException =>
            logger.error(s"Invalid date format: ${e.getMessage}")
            Future.failed(RequestCouldNotBeProcessed)
        }
    }
  }

  private def generateHistory(
    testOrg:        TestOrganisationWithId,
    oasSubmissions: Seq[ObligationsAndSubmissionsMongoSubmission]
  ): Result = {

    val dueDate  = testOrg.organisation.accountingPeriod.endDate.plusMonths(15)
    val canAmend = if (LocalDate.now().isAfter(dueDate)) false else true
    val submissions = oasSubmissions.map(submission =>
      Submission(
        submissionType = submission.submissionType,
        receivedDate = submission.submittedAt.atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
        country = if (submission.submissionType == ORN) Some("FR") else None
      )
    )
    val p2TaxReturnSubmissions = submissions.filter(s => s.submissionType == UKTR || s.submissionType == BTN)
    val girSubmissions         = submissions.filter(s => s.submissionType == BTN || s.submissionType == GIR || s.submissionType == ORN)

    Ok(
      Json.toJson(
        ObligationsAndSubmissionsSuccessResponse(
          ObligationsAndSubmissionsSuccess(
            processingDate = ZonedDateTime.parse(nowZonedDateTime),
            accountingPeriodDetails = Seq(
              AccountingPeriodDetails(
                startDate = testOrg.organisation.accountingPeriod.startDate,
                endDate = testOrg.organisation.accountingPeriod.endDate,
                dueDate = dueDate,
                underEnquiry = false,
                obligations = {
                  val domesticObligation = Seq(
                    Obligation(
                      obligationType = Pillar2TaxReturn,
                      status = if (p2TaxReturnSubmissions.isEmpty) Open else Fulfilled,
                      canAmend = canAmend,
                      submissions = p2TaxReturnSubmissions
                    )
                  )
                  if (!testOrg.organisation.orgDetails.domesticOnly) {
                    domesticObligation :+ Obligation(
                      obligationType = GlobeInformationReturn,
                      status = if (girSubmissions.isEmpty) Open else Fulfilled,
                      canAmend = canAmend,
                      submissions = girSubmissions
                    )
                  } else domesticObligation
                }
              )
            )
          )
        )
      )
    )
  }
}
