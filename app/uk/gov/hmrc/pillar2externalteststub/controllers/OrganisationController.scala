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

import play.api.libs.json.{JsError, JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{OrganisationDetails, OrganisationDetailsRequest}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}
import play.api.Logging

@Singleton
class OrganisationController @Inject() (
  cc:                  ControllerComponents,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def create(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    if (pillar2Id.trim.isEmpty) {
      Future.successful(BadRequest(Json.obj("message" -> "Pillar2 ID is required")))
    } else {
      request.body
        .validate[OrganisationDetailsRequest]
        .fold(
          invalid = errors => Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors)))),
          valid = requestDetails => {
            val details = OrganisationDetails.fromRequest(requestDetails)
            organisationService.createOrganisation(pillar2Id, details).map {
              case Right(created) => Created(Json.toJson(created))
              case Left(error)    => InternalServerError(Json.obj("message" -> error))
            }
          }
        )
    }
  }

  def get(pillar2Id: String): Action[AnyContent] = Action.async { implicit request =>
    organisationService.getOrganisation(pillar2Id).map {
      case Some(org) => Ok(Json.toJson(org))
      case None      => NotFound(Json.obj("message" -> s"Organisation not found for pillar2Id: $pillar2Id"))
    }
  }

  def update(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[OrganisationDetailsRequest]
      .fold(
        invalid = errors => Future.successful(BadRequest(Json.obj("errors" -> JsError.toJson(errors)))),
        valid = requestDetails => {
          val details = OrganisationDetails.fromRequest(requestDetails)
          organisationService.updateOrganisation(pillar2Id, details).map {
            case Right(updated) => Ok(Json.toJson(updated))
            case Left(error)    => InternalServerError(Json.obj("message" -> error))
          }
        }
      )
  }

  def delete(pillar2Id: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"Deleting organisation with pillar2Id: $pillar2Id")
    organisationService.deleteOrganisation(pillar2Id).map {
      case true  => NoContent
      case false => InternalServerError(Json.obj("message" -> "Failed to delete organisation"))
    }
  }
}
