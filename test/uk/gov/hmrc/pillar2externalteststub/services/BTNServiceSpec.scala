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
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqTo}
import org.mockito.Mockito.reset
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, ObligationsAndSubmissionsDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.TaxObligationAlreadyFulfilled
import uk.gov.hmrc.pillar2externalteststub.repositories.{BTNSubmissionRepository, ObligationsAndSubmissionsRepository}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BTNServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with BTNDataFixture
    with TestOrgDataFixture
    with ObligationsAndSubmissionsDataFixture
    with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockBtnRepository)
    reset(mockOasRepository)
    reset(mockOrgService)
  }

  private val mockBtnRepository = mock[BTNSubmissionRepository]
  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]
  protected val service         = new BTNService(mockBtnRepository, mockOasRepository, mockOrgService)(global)

  "BTNService" should {
    "submitBTN" should {
      "fail with TaxObligationAlreadyFulfilled when a BTN submission is the most recent submission for the period" in {
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Seq(btnObligationsAndSubmissionsMongoSubmission)))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitBTN(validPlrId, validBTNRequest)

        result shouldFailWith TaxObligationAlreadyFulfilled

        verify(mockBtnRepository, never).insert(anyString(), any[BTNRequest]())
      }

      "fail with RequestCouldNotBeProcessed for invalid requests" in {
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))
        val invalidRequest = validBTNRequest.copy(
          accountingPeriodFrom = LocalDate.of(2023, 1, 1),
          accountingPeriodTo = LocalDate.of(2022, 12, 31) // End date before start date
        )

        val result = service.submitBTN(validPlrId, invalidRequest)

        result shouldFailWith RequestCouldNotBeProcessed
      }

      "successfully submit a BTN when no existing submission for period" in {
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Seq.empty))
        when(mockBtnRepository.insert(anyString(), any[BTNRequest]()))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[BTNRequest](), anyString(), any[ObjectId]()))
          .thenReturn(Future.successful(true))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitBTN(validPlrId, validBTNRequest)

        result.futureValue mustBe true
        verify(mockBtnRepository).insert(validPlrId, validBTNRequest)
        verify(mockOasRepository).insert(eqTo(validBTNRequest), eqTo(validPlrId), any[ObjectId]())
      }

      "successfully submit a BTN when there is a UKTR submission for the period but no BTN" in {
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Seq(uktrObligationsAndSubmissionsMongoSubmission)))
        when(mockBtnRepository.insert(anyString(), any[BTNRequest]()))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[BTNRequest](), anyString(), any[ObjectId]()))
          .thenReturn(Future.successful(true))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitBTN(validPlrId, validBTNRequest)

        result.futureValue mustBe true
        verify(mockBtnRepository).insert(validPlrId, validBTNRequest)
        verify(mockOasRepository).insert(eqTo(validBTNRequest), eqTo(validPlrId), any[ObjectId]())
      }

      "successfully submit a BTN when there is an older BTN submission for the period that is not the most recent submission" in {
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Seq(uktrObligationsAndSubmissionsMongoSubmission, olderBtnObligationsAndSubmissionsMongoSubmission)))
        when(mockBtnRepository.insert(anyString(), any[BTNRequest]()))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[BTNRequest](), anyString(), any[ObjectId]()))
          .thenReturn(Future.successful(true))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitBTN(validPlrId, validBTNRequest)

        result.futureValue mustBe true
        verify(mockBtnRepository).insert(validPlrId, validBTNRequest)
        verify(mockOasRepository).insert(eqTo(validBTNRequest), eqTo(validPlrId), any[ObjectId]())
      }

      "successfully submit a BTN when there is a BTN submission for a different period" in {
        when(mockOasRepository.findByPillar2Id(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Seq(differentPeriodBtnObligationsAndSubmissionsMongoSubmission)))
        when(mockBtnRepository.insert(anyString(), any[BTNRequest]()))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[BTNRequest](), anyString(), any[ObjectId]()))
          .thenReturn(Future.successful(true))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitBTN(validPlrId, validBTNRequest)

        result.futureValue mustBe true
        verify(mockBtnRepository).insert(validPlrId, validBTNRequest)
        verify(mockOasRepository).insert(eqTo(validBTNRequest), eqTo(validPlrId), any[ObjectId]())
      }
    }
  }
}
