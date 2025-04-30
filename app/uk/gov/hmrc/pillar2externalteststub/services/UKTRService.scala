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

package uk.gov.hmrc.pillar2externalteststub.services

import play.api.Logging
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.generateChargeReference
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.LiabilityReturnSuccess.successfulUKTRResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRLiabilityReturn
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRNilReturn
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRSubmissionError
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.{ObligationsAndSubmissionsRepository, UKTRSubmissionRepository}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UKTRService @Inject() (
  uktrRepository:      UKTRSubmissionRepository,
  oasRepository:       ObligationsAndSubmissionsRepository,
  organisationService: OrganisationService
)(implicit ec:         ExecutionContext)
    extends Logging {

  def submitUKTR(pillar2Id: String, request: UKTRSubmission): Future[UKTRResponse] = {
    logger.info(s"Submitting UKTR for pillar2Id: $pillar2Id")
    for {
      validator <- getValidator(pillar2Id, request)
      _         <- validateRequest(validator, request)
      _         <- validateNoExistingSubmission(pillar2Id)
      _         <- processSubmission(pillar2Id, request)
    } yield createResponse(request)
  }

  def amendUKTR(pillar2Id: String, request: UKTRSubmission): Future[UKTRResponse] = {
    logger.info(s"Amending UKTR for pillar2Id: $pillar2Id")
    for {
      existingSubmission <- getExistingSubmission(pillar2Id)
      validator          <- getValidator(pillar2Id, request)
      _                  <- validateRequest(validator, request)
      _                  <- processSubmission(pillar2Id, request, isAmendment = true)
    } yield createResponse(request, existingSubmission.chargeReference)
  }

  private def getValidator(pillar2Id: String, request: UKTRSubmission): Future[ValidationRule[UKTRSubmission]] =
    request match {
      case _: UKTRLiabilityReturn =>
        UKTRLiabilityReturn.uktrSubmissionValidator(pillar2Id)(organisationService, ec).map(_.asInstanceOf[ValidationRule[UKTRSubmission]])
      case _: UKTRNilReturn =>
        UKTRNilReturn.uktrNilReturnValidator(pillar2Id)(organisationService, ec).map(_.asInstanceOf[ValidationRule[UKTRSubmission]])
      case _ => Future.failed(ETMPBadRequest)
    }

  private def validateRequest(validator: ValidationRule[UKTRSubmission], request: UKTRSubmission): Future[Unit] =
    validator.validate(request) match {
      case cats.data.Validated.Valid(_) => Future.successful(())
      case cats.data.Validated.Invalid(errors) =>
        errors.head match {
          case UKTRSubmissionError(error) => Future.failed(error)
          case _                          => Future.failed(ETMPInternalServerError)
        }
    }

  private def validateNoExistingSubmission(pillar2Id: String): Future[Unit] =
    uktrRepository.findByPillar2Id(pillar2Id).flatMap {
      case Some(_) => Future.failed(TaxObligationAlreadyFulfilled)
      case None    => Future.successful(())
    }

  private def getExistingSubmission(pillar2Id: String): Future[UKTRMongoSubmission] =
    uktrRepository.findByPillar2Id(pillar2Id).flatMap {
      case Some(submission) => Future.successful(submission)
      case None             => Future.failed(RequestCouldNotBeProcessed)
    }

  private def processSubmission(pillar2Id: String, request: UKTRSubmission, isAmendment: Boolean = false): Future[Unit] =
    request match {
      case nilReturn: UKTRNilReturn =>
        if (isAmendment) {
          uktrRepository.update(nilReturn, pillar2Id).flatMap { result =>
            oasRepository.insert(nilReturn, pillar2Id, id = result._1, isAmendment = true).map(_ => ())
          }
        } else {
          uktrRepository.insert(nilReturn, pillar2Id).flatMap { sub =>
            oasRepository.insert(nilReturn, pillar2Id, sub).map(_ => ())
          }
        }
      case liability: UKTRLiabilityReturn =>
        if (isAmendment) {
          uktrRepository.update(liability, pillar2Id).flatMap { result =>
            oasRepository.insert(liability, pillar2Id, id = result._1, isAmendment = true).map(_ => ())
          }
        } else {
          val chargeRef = generateChargeReference()
          uktrRepository.insert(liability, pillar2Id, Some(chargeRef)).flatMap { sub =>
            oasRepository.insert(liability, pillar2Id, sub).map(_ => ())
          }
        }
      case _ => Future.failed(ETMPBadRequest)
    }

  private def createResponse(request: UKTRSubmission, existingChargeRef: Option[String] = None): UKTRResponse = request match {
    case _: UKTRLiabilityReturn => successfulUKTRResponse(existingChargeRef)
    case _: UKTRNilReturn       => successfulNilReturnResponse
    case _ => throw ETMPBadRequest
  }
}
