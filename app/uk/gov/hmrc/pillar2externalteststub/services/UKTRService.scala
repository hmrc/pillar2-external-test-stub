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
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{generateChargeReference, getAmendmentDeadline}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest
import uk.gov.hmrc.pillar2externalteststub.models.uktr.LiabilityReturnSuccess.successfulUKTRResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr.NilReturnSuccess.successfulNilReturnResponse
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.{ObligationsAndSubmissionsRepository, UKTRSubmissionRepository}
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationRule

import java.time.LocalDate
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class UKTRService @Inject() (
  uktrRepository:      UKTRSubmissionRepository,
  oasRepository:       ObligationsAndSubmissionsRepository,
  organisationService: OrganisationService
)(using ec:            ExecutionContext)
    extends Logging {

  def submitUKTR(pillar2Id: String, request: UKTRSubmission): Future[UKTRResponse] = {
    logger.info(s"Submitting UKTR for pillar2Id: $pillar2Id")
    for {
      validator <- getValidator(pillar2Id, request)
      _         <- validateRequest(validator, request)
      _         <- validateNoExistingSubmission(pillar2Id)
      chargeRef = if request.isInstanceOf[UKTRLiabilityReturn] then Some(generateChargeReference()) else None
      _ <- processSubmission(pillar2Id, request, chargeRef = chargeRef)
      _ <- organisationService.makeOrganisationActive(pillar2Id)
    } yield createResponse(request, chargeRef)
  }

  def amendUKTR(pillar2Id: String, request: UKTRSubmission): Future[UKTRResponse] = {
    logger.info(s"Amending UKTR for pillar2Id: $pillar2Id")
    for {
      existingSubmission <- getExistingSubmission(pillar2Id)
      _                  <- amendmentWindowCheck(pillar2Id)
      validator          <- getValidator(pillar2Id, request)
      _                  <- validateRequest(validator, request)
      maybeChargeRef     <- processSubmission(pillar2Id, request, isAmendment = true)
      chargeRef = if existingSubmission.chargeReference.isEmpty && request.isInstanceOf[UKTRLiabilityReturn] then maybeChargeRef
                  else existingSubmission.chargeReference
      _ <- organisationService.makeOrganisationActive(pillar2Id)
    } yield createResponse(request, chargeRef)
  }

  def getValidator(pillar2Id: String, request: UKTRSubmission): Future[ValidationRule[UKTRSubmission]] =
    request match {
      case _: UKTRLiabilityReturn =>
        UKTRLiabilityReturn.uktrSubmissionValidator(pillar2Id)(using organisationService, ec).map(_.asInstanceOf[ValidationRule[UKTRSubmission]])
      case _: UKTRNilReturn =>
        UKTRNilReturn.uktrNilReturnValidator(pillar2Id)(using organisationService, ec).map(_.asInstanceOf[ValidationRule[UKTRSubmission]])
      case _ => Future.failed(HIPBadRequest())
    }

  def validateRequest(validator: ValidationRule[UKTRSubmission], request: UKTRSubmission): Future[Unit] =
    validator.validate(request) match {
      case cats.data.Validated.Valid(_) => Future.successful(())
      case cats.data.Validated.Invalid(errors) =>
        errors.head match {
          case UKTRSubmissionError(error) => Future.failed(error)
          case _                          => Future.failed(ETMPInternalServerError)
        }
    }

  def amendmentWindowCheck(pillar2Id: String): Future[Unit] =
    organisationService.getOrganisation(pillar2Id).flatMap { org =>
      val amendmentsAllowed: Boolean = !LocalDate.now.isAfter(getAmendmentDeadline(org.organisation.orgDetails.registrationDate))
      if amendmentsAllowed then Future.successful(()) else Future.failed(RequestCouldNotBeProcessed)
    }

  def validateNoExistingSubmission(pillar2Id: String): Future[Unit] =
    uktrRepository.findByPillar2Id(pillar2Id).flatMap {
      case Some(_) => Future.failed(TaxObligationAlreadyFulfilled)
      case None    => Future.successful(())
    }

  def getExistingSubmission(pillar2Id: String): Future[UKTRMongoSubmission] =
    uktrRepository.findByPillar2Id(pillar2Id).flatMap {
      case Some(submission) => Future.successful(submission)
      case None             => Future.failed(RequestCouldNotBeProcessed)
    }

  def processSubmission(
    pillar2Id:   String,
    request:     UKTRSubmission,
    chargeRef:   Option[String] = None,
    isAmendment: Boolean = false
  ): Future[Option[String]] =
    request match {
      case submission: UKTRSubmission =>
        if isAmendment then {
          uktrRepository.update(submission, pillar2Id).flatMap { case (objectId, chargeRef) =>
            oasRepository
              .insert(submission, pillar2Id, objectId, isAmendment = true)
              .map(_ => chargeRef)
          }
        } else {
          uktrRepository.insert(submission, pillar2Id, chargeRef).flatMap { objectId =>
            oasRepository
              .insert(submission, pillar2Id, objectId)
              .map(_ => chargeRef)
          }
        }
      case _ => Future.failed(HIPBadRequest())
    }

  def createResponse(request: UKTRSubmission, existingChargeRef: Option[String]): UKTRResponse = request match {
    case _: UKTRLiabilityReturn => successfulUKTRResponse(existingChargeRef)
    case _: UKTRNilReturn       => successfulNilReturnResponse
    case _ => throw HIPBadRequest()
  }
}
