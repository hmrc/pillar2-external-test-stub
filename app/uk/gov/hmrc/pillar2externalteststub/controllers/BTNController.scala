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

package uk.gov.hmrc.pillar2externalteststub.controllers

import play.api.Logging
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents, Result}
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{ServerErrorPlrId, pillar2Regex}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNErrorResponse.{BTN_ERROR_400, BTN_ERROR_500}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNFailureResponse._
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNSuccessResponse.BTN_SUCCESS_201
import uk.gov.hmrc.pillar2externalteststub.models.btn._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.repositories.BTNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class BTNController @Inject() (
  cc:                  ControllerComponents,
  authFilter:          AuthActionFilter,
  repository:          BTNSubmissionRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitBTN: Action[JsValue] = (Action(parse.json) andThen authFilter).async { implicit request =>
    validatePillar2Id(request.headers.get("X-Pillar2-Id")).fold(
      error => Future.successful(error),
      pillar2Id =>
        request.body
          .validate[BTNRequest]
          .fold(
            _ => Future.successful(BadRequest(Json.toJson(BTN_ERROR_400("Invalid request payload")))),
            btnRequest =>
              if (!btnRequest.accountingPeriodValid) {
                Future.successful(UnprocessableEntity(Json.toJson(BTN_REQUEST_INVALID_003)))
              } else {
                handleSubmission(pillar2Id, btnRequest)
              }
          )
    )
  }

  private def validatePillar2Id(pillar2Id: Option[String]): Either[Result, String] =
    pillar2Id
      .filter(pillar2Regex.matches)
      .toRight(UnprocessableEntity(Json.toJson(BTN_PILLAR2_MISSING_OR_INVALID_002)))

  private def handleSubmission(pillar2Id: String, request: BTNRequest): Future[Result] =
    pillar2Id match {
      case ServerErrorPlrId => Future.successful(InternalServerError(Json.toJson(BTN_ERROR_500())))
      case _ =>
        organisationService
          .getOrganisation(pillar2Id)
          .flatMap { testOrg =>
            if (
              testOrg.organisation.accountingPeriod.startDate == request.accountingPeriodFrom &&
              testOrg.organisation.accountingPeriod.endDate == request.accountingPeriodTo
            ) {
              repository.findByPillar2Id(pillar2Id).flatMap { submissions =>
                if (
                  submissions.exists(submission =>
                    submission.accountingPeriodFrom == request.accountingPeriodFrom
                      && submission.accountingPeriodTo == request.accountingPeriodTo
                  )
                )
                  Future.successful(UnprocessableEntity(Json.toJson(BTN_DUPLICATE_SUBMISSION_004)))
                else repository.insert(pillar2Id, request).map(_ => Created(Json.toJson(BTN_SUCCESS_201)))
              }
            } else Future.successful(UnprocessableEntity(Json.toJson(BTN_REQUEST_INVALID_003)))
          }
          .recoverWith { case _: OrganisationNotFound =>
            Future.successful(UnprocessableEntity(Json.toJson(BTN_BUSINESS_PARTNER_NOT_ACTIVE_007)))
          }
    }
}
