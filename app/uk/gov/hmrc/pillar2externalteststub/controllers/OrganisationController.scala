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
import play.api.mvc._
import uk.gov.hmrc.pillar2externalteststub.models.error.{EmptyRequestBody, InvalidJson}
import uk.gov.hmrc.pillar2externalteststub.models.organisation.{TestOrganisation, TestOrganisationRequest}
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

@Singleton
class OrganisationController @Inject() (
  cc:                  ControllerComponents,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def create(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    if (pillar2Id.trim.isEmpty) {
      Future.failed(EmptyRequestBody)
    } else {
      request.body
        .validate[TestOrganisationRequest]
        .fold(
          _ => Future.failed(InvalidJson),
          valid = requestDetails => {
            val details = TestOrganisation.fromRequest(requestDetails)
            organisationService.createOrganisation(pillar2Id, details).map { created =>
              Created(Json.toJson(created))
            }
          }
        )
    }
  }

  def get(pillar2Id: String): Action[AnyContent] = Action.async { implicit request =>
    organisationService.getOrganisation(pillar2Id).map { org =>
      Ok(Json.toJson(org))
    }
  }

  def update(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body
      .validate[TestOrganisationRequest]
      .fold(
        _ => Future.failed(InvalidJson),
        valid = requestDetails => {
          val details = TestOrganisation.fromRequest(requestDetails)
          organisationService.updateOrganisation(pillar2Id, details).map { updated =>
            Ok(Json.toJson(updated))
          }
        }
      )
  }

  def delete(pillar2Id: String): Action[AnyContent] = Action.async { implicit request =>
    logger.info(s"Deleting organisation with pillar2Id: $pillar2Id")
    organisationService.deleteOrganisation(pillar2Id).map { _ =>
      NoContent
    }
  }
}
