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
import org.mockito.ArgumentMatchers.{any, anyString, eq as eqTo}
import org.mockito.Mockito.*
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.{GIRDataFixture, ObligationsAndSubmissionsDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{RequestCouldNotBeProcessed, TaxObligationAlreadyFulfilled}
import uk.gov.hmrc.pillar2externalteststub.models.gir.GIRRequest
import uk.gov.hmrc.pillar2externalteststub.repositories.{GIRSubmissionRepository, ObligationsAndSubmissionsRepository}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GIRServiceSpec
    extends AnyWordSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with GIRDataFixture
    with TestOrgDataFixture
    with ObligationsAndSubmissionsDataFixture
    with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockGirRepository)
    reset(mockOasRepository)
    reset(mockOrgService)
  }

  private val mockGirRepository = mock[GIRSubmissionRepository]
  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]
  protected val service         = new GIRService(mockGirRepository, mockOasRepository, mockOrgService)(using global)

  "GIRService" should {
    "submitGIR" should {
      "fail with TaxObligationAlreadyFulfilled when a GIR submission is the most recent submission for the period" in {
        when(mockGirRepository.findByPillar2Id(anyString()))
          .thenReturn(Future.successful(Seq(girMongoSubmission)))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitGIR(validPlrId, validGIRRequest)

        result shouldFailWith TaxObligationAlreadyFulfilled

        verify(mockGirRepository, never).insert(anyString(), any[GIRRequest]())
      }

      "fail with RequestCouldNotBeProcessed for invalid requests" in {
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))
        val invalidRequest = validGIRRequest.copy(
          accountingPeriodFrom = LocalDate.of(2023, 1, 1),
          accountingPeriodTo = LocalDate.of(2022, 12, 31) // End date before start date
        )

        val result = service.submitGIR(validPlrId, invalidRequest)

        result shouldFailWith RequestCouldNotBeProcessed
      }

      "successfully submit a GIR when no existing submission for period" in {
        when(mockGirRepository.findByPillar2Id(anyString()))
          .thenReturn(Future.successful(Seq.empty))
        when(mockGirRepository.insert(anyString(), any[GIRRequest]()))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[GIRRequest](), anyString(), any[ObjectId](), eqTo(false)))
          .thenReturn(Future.successful(true))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitGIR(validPlrId, validGIRRequest)

        result.futureValue mustBe true
        verify(mockGirRepository).insert(validPlrId, validGIRRequest)
        verify(mockOasRepository).insert(eqTo(validGIRRequest), eqTo(validPlrId), any[ObjectId](), eqTo(false))
      }

      "successfully submit a GIR when there is a GIR submission for a different period" in {
        when(mockGirRepository.findByPillar2Id(anyString()))
          .thenReturn(Future.successful(Seq(differentPeriodGirMongoSubmission)))
        when(mockGirRepository.insert(anyString(), any[GIRRequest]()))
          .thenReturn(Future.successful(new ObjectId()))
        when(mockOasRepository.insert(any[GIRRequest](), anyString(), any[ObjectId](), eqTo(false)))
          .thenReturn(Future.successful(true))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitGIR(validPlrId, validGIRRequest)

        result.futureValue mustBe true
        verify(mockGirRepository).insert(validPlrId, validGIRRequest)
        verify(mockOasRepository).insert(eqTo(validGIRRequest), eqTo(validPlrId), any[ObjectId](), eqTo(false))
      }
    }
  }
}
