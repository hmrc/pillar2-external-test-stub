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
import play.api.libs.json._
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.controllers.actions.AuthActionFilter
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.helpers.SubscriptionHelper.retrieveSubscription
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.LiabilityReturnSuccess.successfulUKTRResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.{MissingPLRReference, SubscriptionNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSimpleError.{InvalidJsonError, SAPError}
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.validation.syntax.ValidateOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class SubmitUKTRController @Inject() (
  cc:          ControllerComponents,
  authFilter:  AuthActionFilter,
  repository:  UKTRSubmissionRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def submitUKTR: Action[JsValue] = (Action andThen authFilter).async(parse.json) { implicit request =>
    request.headers.get("X-Pillar2-Id") match {
      case None =>
        logger.warn("X-Pillar2-Id header is missing")
        Future.successful(UnprocessableEntity(Json.toJson(MissingPLRReference)))
      case Some(plrReference) =>
        plrReference match {
          case ServerErrorPlrId => Future.successful(InternalServerError(Json.toJson(SAPError)))
          case _ =>
            retrieveSubscription(plrReference)._2 match {
              case _: SubscriptionSuccessResponse => validateRequest(plrReference, request)
              case _ => Future.successful(UnprocessableEntity(Json.toJson(SubscriptionNotFound(plrReference))))
            }
        }
    }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] =
    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        Future.successful(uktrRequest.validate(plrReference).toEither).flatMap {
          case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
          case Right(_)     => repository.insert(uktrRequest, plrReference).map(_ => Created(Json.toJson(successfulUKTRResponse)))
        }
      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        Future.successful(nilReturnRequest.validate(plrReference).toEither).flatMap {
          case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
          case Right(_)     => repository.insert(nilReturnRequest, plrReference).map(_ => Created(Json.toJson(successfulNilReturnResponse)))
        }
      case JsError(errors) =>
        val errorMessage = errors
          .map { case (path, validationErrors) =>
            val fieldName     = path.toJsonString
            val errorMessages = validationErrors.map(_.message).mkString(", ")
            s"Field: $fieldName: $errorMessages"
          }
          .mkString("; ")
        Future.successful(BadRequest(Json.toJson(InvalidJsonError(errorMessage))))
      case _ => Future.successful(BadRequest(Json.toJson(InvalidJsonError())))
    }
}
