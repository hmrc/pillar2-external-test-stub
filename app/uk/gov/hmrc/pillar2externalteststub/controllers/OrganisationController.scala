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

import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.models.organisation.OrganisationDetails
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class OrganisationController @Inject()(
  cc: ControllerComponents,
  organisationService: OrganisationService
)(implicit ec: ExecutionContext)
    extends BackendController(cc) {

  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withPillar2Id { pillar2Id =>
      request.body
        .validate[OrganisationDetails]
        .fold(
          invalid = _ => Future.successful(BadRequest("Invalid organisation details")),
          valid = details =>
            organisationService.createOrganisation(pillar2Id, details).map {
              case Right(created) => Created(Json.toJson(created))
              case Left(error)    => InternalServerError(error)
            }
        )
    }
  }

  def get(pillar2Id: String): Action[AnyContent] = Action.async { implicit request =>
    organisationService.getOrganisation(pillar2Id).map {
      case Some(org) => Ok(Json.toJson(org))
      case None      => NotFound
    }
  }

  def update(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[OrganisationDetails]
      .fold(
        invalid = _ => Future.successful(BadRequest("Invalid organisation details")),
        valid = details =>
          organisationService.updateOrganisation(pillar2Id, details).map {
            case Right(updated) => Ok(Json.toJson(updated))
            case Left(error)    => InternalServerError(error)
          }
      )
  }

  def delete(pillar2Id: String): Action[AnyContent] = Action.async { implicit request =>
    organisationService.deleteOrganisation(pillar2Id).map {
      case true  => NoContent
      case false => InternalServerError("Failed to delete organisation")
    }
  }

  private def withPillar2Id[A](f: String => Future[Result])(implicit request: Request[A]): Future[Result] = {
    request.headers
      .get("X-Pillar2-ID")
      .map(f)
      .getOrElse(Future.successful(BadRequest("Missing Pillar2 ID in headers")))
  }
} 