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
import uk.gov.hmrc.pillar2externalteststub.helpers.SubscriptionHelper.retrieveSubscription
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper._
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.subscription.SubscriptionSuccessResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.LiabilityReturnSuccess.successfulUKTRResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRDetailedError.{MissingPLRReference, SubscriptionNotFound}
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSimpleError.{InvalidJsonError, SAPError}
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.repositories.UKTRSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.validation.syntax.ValidateOps
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class AmendUKTRController @Inject() (
  cc:          ControllerComponents,
  authFilter:  AuthActionFilter,
  repository:  UKTRSubmissionRepository
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def amendUKTR: Action[AnyContent] = (Action andThen authFilter).async { implicit request =>
    def processJsonRequest(jsValue: JsValue) =
      request.headers.get("X-Pillar2-Id") match {
        case None =>
          logger.warn("X-Pillar2-Id header is missing")
          Future.successful(UnprocessableEntity(Json.toJson(MissingPLRReference)))
        case Some(plrReference) =>
          plrReference match {
            case ServerErrorPlrId => Future.successful(InternalServerError(Json.toJson(SAPError)))
            case _ =>
              retrieveSubscription(plrReference)._2 match {
                case _: SubscriptionSuccessResponse => validateRequest(plrReference, request.map(_ => jsValue))
                case _ => Future.successful(UnprocessableEntity(Json.toJson(SubscriptionNotFound(plrReference))))
              }
          }
      }

    request.contentType match {
      case Some("application/json") =>
        request.body.asJson match {
          case Some(json) => processJsonRequest(json)
          case None       => Future.successful(BadRequest(Json.toJson(InvalidJsonError("Invalid JSON"))))
        }
      case _ =>
        logger.warn("Invalid or missing Content-Type header")
        Future.successful(UnsupportedMediaType)
    }
  }

  def validateRequest(plrReference: String, request: Request[JsValue])(implicit ec: ExecutionContext): Future[Result] =
    request.body.validate[UKTRSubmission] match {
      case JsSuccess(uktrRequest: UKTRLiabilityReturn, _) =>
        uktrRequest.validate(plrReference).toEither match {
          case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
          case Right(_) =>
            Option(repository.findByPillar2Id(plrReference))
              .getOrElse(Future.successful(None))
              .flatMap { _ =>
                repository
                  .update(uktrRequest, plrReference)
                  .map {
                    case Right(_)    => Ok(Json.toJson(successfulUKTRResponse))
                    case Left(error) => UnprocessableEntity(Json.toJson(error))
                  }
                  .recover { case e: DatabaseError =>
                    UnprocessableEntity(
                      Json.toJson(
                        DetailedErrorResponse(
                          UKTRDetailedError(
                            processingDate = nowZonedDateTime,
                            code = REQUEST_COULD_NOT_BE_PROCESSED_003,
                            text = e.getMessage
                          )
                        )
                      )
                    )
                  }
              }
        }
      case JsSuccess(nilReturnRequest: UKTRNilReturn, _) =>
        nilReturnRequest.validate(plrReference).toEither match {
          case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
          case Right(_) =>
            Option(repository.findByPillar2Id(plrReference))
              .getOrElse(Future.successful(None))
              .flatMap { _ =>
                repository
                  .update(nilReturnRequest, plrReference)
                  .map {
                    case Right(_)    => Ok(Json.toJson(successfulNilReturnResponse))
                    case Left(error) => UnprocessableEntity(Json.toJson(error))
                  }
                  .recover { case e: DatabaseError =>
                    UnprocessableEntity(
                      Json.toJson(
                        DetailedErrorResponse(
                          UKTRDetailedError(
                            processingDate = nowZonedDateTime,
                            code = REQUEST_COULD_NOT_BE_PROCESSED_003,
                            text = e.getMessage
                          )
                        )
                      )
                    )
                  }
              }
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

  def amendLiabilityReturn(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UKTRLiabilityReturn] { submission =>
      Future.successful(submission.validate(pillar2Id).toEither).flatMap {
        case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
        case Right(_) =>
          repository
            .update(submission, pillar2Id)
            .map {
              case Right(_)    => Ok(Json.toJson(successfulUKTRResponse))
              case Left(error) => UnprocessableEntity(Json.toJson(error))
            }
            .recover { case e: DatabaseError =>
              UnprocessableEntity(
                Json.toJson(
                  DetailedErrorResponse(
                    UKTRDetailedError(
                      processingDate = nowZonedDateTime,
                      code = REQUEST_COULD_NOT_BE_PROCESSED_003,
                      text = e.getMessage
                    )
                  )
                )
              )
            }
      }
    }
  }

  def amendNilReturn(pillar2Id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    withJsonBody[UKTRNilReturn] { submission =>
      Future.successful(submission.validate(pillar2Id).toEither).flatMap {
        case Left(errors) => UKTRErrorTransformer.from422ToJson(errors)
        case Right(_) =>
          repository
            .update(submission, pillar2Id)
            .map {
              case Right(_)    => Ok(Json.toJson(successfulNilReturnResponse))
              case Left(error) => UnprocessableEntity(Json.toJson(error))
            }
            .recover { case e: DatabaseError =>
              UnprocessableEntity(
                Json.toJson(
                  DetailedErrorResponse(
                    UKTRDetailedError(
                      processingDate = nowZonedDateTime,
                      code = REQUEST_COULD_NOT_BE_PROCESSED_003,
                      text = e.getMessage
                    )
                  )
                )
              )
            }
      }
    }
  }
}
