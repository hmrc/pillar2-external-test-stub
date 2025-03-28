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
import org.mockito.Mockito.{never, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.btn.BTNRequest
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.DuplicateSubmission
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.repositories.{BTNSubmissionRepository, ObligationsAndSubmissionsRepository}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BTNServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with BTNDataFixture with TestOrgDataFixture {

  private val mockBtnRepository = mock[BTNSubmissionRepository]
  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]
  protected val service         = new BTNService(mockBtnRepository, mockOasRepository, mockOrgService)(global)

  "BTNService" should {
    "submitBTN" should {
      "fail with DuplicateSubmission when a BTN submission exists" in {
        when(mockBtnRepository.findByPillar2Id(anyString()))
          .thenReturn(Future.successful(Seq(btnMongoSubmission)))
        when(mockOrgService.getOrganisation(anyString()))
          .thenReturn(Future.successful(domesticOrganisation))

        val result = service.submitBTN(validPlrId, validBTNRequest)

        result shouldFailWith DuplicateSubmission

        verify(mockBtnRepository, never).insert(anyString(), any[BTNRequest]())
      }

      "fail with RequestCouldNotBeProcessed for invalid requests" in {
        // Setup
        val invalidRequest = validBTNRequest.copy(
          accountingPeriodFrom = LocalDate.of(2023, 1, 1),
          accountingPeriodTo = LocalDate.of(2022, 12, 31) // End date before start date
        )

        // Execute
        val result = service.submitBTN(validPlrId, invalidRequest)

        // Verify
        whenReady(result.failed) { e =>
          e mustBe RequestCouldNotBeProcessed
        }
        verify(mockOasRepository, never).findByPillar2Id(anyString(), any[LocalDate], any[LocalDate])
      }
      "successfully submit a BTN when no existing submission for period" in {
        when(mockBtnRepository.findByPillar2Id(anyString()))
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
    }
  }
}
