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

package uk.gov.hmrc.pillar2externalteststub.services
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq => eqTo}
import org.mockito.Mockito.when
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.Json
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{AmendmentWindowMonths, FirstAccountingPeriodDueDateFromRegistrationMonths}
import uk.gov.hmrc.pillar2externalteststub.helpers.{ObligationsAndSubmissionsDataFixture, TestOrgDataFixture, UKTRDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.common.BaseSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.organisation.TestOrganisationWithId
import uk.gov.hmrc.pillar2externalteststub.models.uktr._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.mongo.UKTRMongoSubmission
import uk.gov.hmrc.pillar2externalteststub.repositories.{ObligationsAndSubmissionsRepository, UKTRSubmissionRepository}

import java.time.{Instant, LocalDate}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class UKTRServiceSpec
    extends AnyFreeSpec
    with Matchers
    with MockitoSugar
    with UKTRDataFixture
    with TestOrgDataFixture
    with ObligationsAndSubmissionsDataFixture {

  private val mockUKTRRepository = mock[UKTRSubmissionRepository]
  private val mockOASRepository  = mock[ObligationsAndSubmissionsRepository]
  private val service            = new UKTRService(mockUKTRRepository, mockOASRepository, mockOrgService)

  "UKTRService" - {
    "when submitting a return" - {
      "should successfully submit a liability return" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(nonDomesticOrganisation))
        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(None))
        when(mockUKTRRepository.insert(any[UKTRLiabilityReturn], eqTo(validPlrId), any[Option[String]]))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOASRepository.insert(any[BaseSubmission], eqTo(validPlrId), any[ObjectId], eqTo(false)))
          .thenReturn(Future.successful(true))
        when(mockOrgService.makeOrganisatonActive(eqTo(validPlrId)))
          .thenReturn(Future.successful(()))

        val result = Await.result(service.submitUKTR(validPlrId, liabilitySubmission), 5.seconds)
        result match {
          case LiabilitySuccessResponse(success) =>
            success.processingDate   should not be empty
            success.formBundleNumber should not be empty
            success.chargeReference  should not be empty
          case _ => fail("Expected LiabilitySuccessResponse")
        }
      }

      "should successfully submit a nil return" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(domesticOrganisation))
        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(None))
        when(mockUKTRRepository.insert(any[UKTRNilReturn], eqTo(validPlrId), any[Option[String]]))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOASRepository.insert(any[BaseSubmission], eqTo(validPlrId), any[ObjectId], eqTo(false)))
          .thenReturn(Future.successful(true))
        when(mockOrgService.makeOrganisatonActive(eqTo(validPlrId)))
          .thenReturn(Future.successful(()))

        val result = Await.result(service.submitUKTR(validPlrId, nilSubmission), 5.seconds)
        result match {
          case NilSuccessResponse(success) =>
            success.processingDate   should not be empty
            success.formBundleNumber should not be empty
          case _ => fail("Expected NilSuccessResponse")
        }
      }

      "should fail with TaxObligationAlreadyFulfilled when submitting twice in a row" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(nonDomesticOrganisation))
        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val result = service.submitUKTR(validPlrId, liabilitySubmission)
        result shouldFailWith TaxObligationAlreadyFulfilled
      }
    }

    "when amending a return" - {
      "should successfully amend a liability return" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(nonDomesticOrganisation))

        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        when(mockUKTRRepository.update(any[UKTRLiabilityReturn], eqTo(validPlrId)))
          .thenReturn(Future.successful((new ObjectId(), Some(chargeReference))))
        when(mockOASRepository.insert(any[BaseSubmission], eqTo(validPlrId), any[ObjectId], eqTo(true)))
          .thenReturn(Future.successful(true))
        when(mockOrgService.makeOrganisatonActive(eqTo(validPlrId)))
          .thenReturn(Future.successful(()))

        val result = Await.result(service.amendUKTR(validPlrId, liabilitySubmission), 5.seconds)
        result match {
          case LiabilitySuccessResponse(success) =>
            success.processingDate    should not be empty
            success.formBundleNumber  should not be empty
            success.chargeReference shouldBe chargeReference
          case _ => fail("Expected LiabilitySuccessResponse")
        }
      }

      "should successfully amend a nil return" in {
        val existingSubmission = UKTRMongoSubmission(
          _id = new ObjectId(),
          pillar2Id = validPlrId,
          chargeReference = Some("existing-ref"),
          data = nilSubmission,
          submittedAt = Instant.now()
        )

        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(domesticOrganisation))

        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(Some(existingSubmission)))

        when(mockUKTRRepository.update(any[UKTRNilReturn], eqTo(validPlrId)))
          .thenReturn(Future.successful((new ObjectId(), Some("existing-ref"))))
        when(mockOASRepository.insert(any[BaseSubmission], eqTo(validPlrId), any[ObjectId], eqTo(true)))
          .thenReturn(Future.successful(true))
        when(mockOrgService.makeOrganisatonActive(eqTo(validPlrId)))
          .thenReturn(Future.successful(()))

        val result = Await.result(service.amendUKTR(validPlrId, nilSubmission), 5.seconds)
        result match {
          case NilSuccessResponse(success) =>
            success.processingDate   should not be empty
            success.formBundleNumber should not be empty
          case _ => fail("Expected NilSuccessResponse")
        }
      }

      "should fail with RequestCouldNotBeProcessed when no existing submission is found" in {
        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(domesticOrganisation))

        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(None))

        val result = service.amendUKTR(validPlrId, liabilitySubmission)
        result shouldFailWith RequestCouldNotBeProcessed
      }

      "should fail with RequestCouldNotBeProcessed when the amendment deadline has elapsed" in {
        val registrationDateBeforeAmendmentWindow: LocalDate = LocalDate
          .now()
          .minusMonths(FirstAccountingPeriodDueDateFromRegistrationMonths)
          .minusMonths(AmendmentWindowMonths)

        val testOrg: TestOrganisationWithId =
          configurableRegistrationDate.replace(registrationDateBeforeAmendmentWindow)(nonDomesticOrganisation)

        when(mockOrgService.getOrganisation(eqTo(validPlrId)))
          .thenReturn(Future.successful(testOrg))
        when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
          .thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))

        val result = service.amendUKTR(validPlrId, liabilitySubmission)
        result shouldFailWith RequestCouldNotBeProcessed
      }
    }

    "validation failures" - {
      "for liability returns" - {
        "should fail when total liability does not match sum of components" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(nonDomesticOrganisation))

          val liabilityReturn = liabilitySubmission.asInstanceOf[UKTRLiabilityReturn]
          val invalidSubmission = liabilityReturn.copy(
            obligationMTT = true,
            liabilities = liabilityReturn.liabilities.copy(
              totalLiability = BigDecimal(50000.00),
              totalLiabilityDTT = BigDecimal(100),
              totalLiabilityIIR = BigDecimal(100),
              totalLiabilityUTPR = BigDecimal(100)
            )
          )

          val result = service.submitUKTR(validPlrId, invalidSubmission)
          result shouldFailWith InvalidTotalLiability
        }

        "should succeed when submitting with obligationMTT false and both IIR and UTPR amounts are zero" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(nonDomesticOrganisation))
          when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
            .thenReturn(Future.successful(None))
          when(mockUKTRRepository.insert(any[UKTRLiabilityReturn], eqTo(validPlrId), any[Option[String]]))
            .thenReturn(Future.successful(new ObjectId()))
          when(mockOASRepository.insert(any[BaseSubmission], eqTo(validPlrId), any[ObjectId], eqTo(false)))
            .thenReturn(Future.successful(true))
          when(mockOrgService.makeOrganisatonActive(eqTo(validPlrId)))
            .thenReturn(Future.successful(()))

          val liabilityReturn = liabilitySubmission.asInstanceOf[UKTRLiabilityReturn]
          val validSubmission = liabilityReturn.copy(
            obligationMTT = false,
            liabilities = liabilityReturn.liabilities.copy(
              electionUTPRSingleMember = false,
              numberSubGroupUTPR = 0,
              totalLiability = BigDecimal(100),
              totalLiabilityIIR = BigDecimal(0),
              totalLiabilityUTPR = BigDecimal(0),
              liableEntities =
                liabilityReturn.liabilities.liableEntities.map(entity => entity.copy(amountOwedIIR = BigDecimal(0), amountOwedUTPR = BigDecimal(0)))
            )
          )

          val result = Await.result(service.submitUKTR(validPlrId, validSubmission), 5.seconds)
          result match {
            case LiabilitySuccessResponse(success) =>
              success.processingDate   should not be empty
              success.formBundleNumber should not be empty
              success.chargeReference  should not be empty
            case _ => fail("Expected LiabilitySuccessResponse")
          }
        }

        "should succeed when amending with obligationMTT false and both IIR and UTPR amounts are zero" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(nonDomesticOrganisation))
          when(mockUKTRRepository.findByPillar2Id(eqTo(validPlrId)))
            .thenReturn(Future.successful(Some(validGetByPillar2IdResponse)))
          when(mockUKTRRepository.update(any[UKTRLiabilityReturn], eqTo(validPlrId)))
            .thenReturn(Future.successful((new ObjectId(), Some(chargeReference))))
          when(mockOASRepository.insert(any[BaseSubmission], eqTo(validPlrId), any[ObjectId], eqTo(true)))
            .thenReturn(Future.successful(true))
          when(mockOrgService.makeOrganisatonActive(eqTo(validPlrId)))
            .thenReturn(Future.successful(()))

          val liabilityReturn = liabilitySubmission.asInstanceOf[UKTRLiabilityReturn]
          val validSubmission = liabilityReturn.copy(
            obligationMTT = false,
            liabilities = liabilityReturn.liabilities.copy(
              electionUTPRSingleMember = false,
              numberSubGroupUTPR = 0,
              totalLiability = BigDecimal(100),
              totalLiabilityIIR = BigDecimal(0),
              totalLiabilityUTPR = BigDecimal(0),
              liableEntities =
                liabilityReturn.liabilities.liableEntities.map(entity => entity.copy(amountOwedIIR = BigDecimal(0), amountOwedUTPR = BigDecimal(0)))
            )
          )

          val result = Await.result(service.amendUKTR(validPlrId, validSubmission), 5.seconds)
          result match {
            case LiabilitySuccessResponse(success) =>
              success.processingDate    should not be empty
              success.formBundleNumber  should not be empty
              success.chargeReference shouldBe chargeReference
            case _ => fail("Expected LiabilitySuccessResponse")
          }
        }

        "should fail when liable entities is empty" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(domesticOrganisation))

          val liabilityReturn = liabilitySubmission.asInstanceOf[UKTRLiabilityReturn]
          val invalidSubmission = liabilityReturn.copy(
            liabilities = liabilityReturn.liabilities.copy(
              electionDTTSingleMember = false,
              electionUTPRSingleMember = false,
              numberSubGroupDTT = 0,
              numberSubGroupUTPR = 0,
              liableEntities = Seq.empty
            )
          )

          val result = service.submitUKTR(validPlrId, invalidSubmission)
          result shouldFailWith InvalidReturn
        }

        "should fail when ukChargeableEntityName is invalid" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(nonDomesticOrganisation))

          val invalidSubmission = Json.fromJson[UKTRLiabilityReturn](invalidUkChargeableEntityNameRequestBody).get

          val result = service.submitUKTR(validPlrId, invalidSubmission)
          result shouldFailWith InvalidReturn
        }

        "should fail when idType is invalid" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(nonDomesticOrganisation))

          val invalidSubmission = Json.fromJson[UKTRLiabilityReturn](invalidIdTypeRequestBody).get

          val result = service.submitUKTR(validPlrId, invalidSubmission)
          result shouldFailWith InvalidReturn
        }

        "should fail when idValue is invalid" in {
          when(mockOrgService.getOrganisation(eqTo(validPlrId)))
            .thenReturn(Future.successful(nonDomesticOrganisation))

          val invalidSubmission = Json.fromJson[UKTRLiabilityReturn](invalidIdValueLengthExceeds15RequestBody).get

          val result = service.submitUKTR(validPlrId, invalidSubmission)
          result shouldFailWith InvalidReturn
        }
      }
    }
  }
}
