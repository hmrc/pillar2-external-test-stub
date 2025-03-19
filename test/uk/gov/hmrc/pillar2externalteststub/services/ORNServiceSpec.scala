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
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.ORNDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{RequestCouldNotBeProcessed, TaxObligationAlreadyFulfilled}
import uk.gov.hmrc.pillar2externalteststub.models.orn.ORNRequest
import uk.gov.hmrc.pillar2externalteststub.repositories.{ORNSubmissionRepository, ObligationsAndSubmissionsRepository}

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class ORNServiceSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with ORNDataFixture {

  private val mockRepository    = mock[ORNSubmissionRepository]
  private val mockOasRepository = mock[ObligationsAndSubmissionsRepository]
  private val service           = new ORNService(mockRepository, mockOasRepository)

  "ORNService" should {
    "submitORN" should {
      "successfully submit a new ORN when no existing submission exists" in {
        when(mockRepository.findByPillar2IdAndAccountingPeriod(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))
        when(mockRepository.insert(anyString(), any[ORNRequest]())).thenReturn(Future.successful(ObjectId.get()))
        when(mockOasRepository.insert(any[ORNRequest](), anyString(), any[ObjectId]())).thenReturn(Future.successful(true))

        val result = service.submitORN(validPlrId, validORNRequest)

        result.futureValue mustBe true
        verify(mockRepository).insert(validPlrId, validORNRequest)
        verify(mockOasRepository).insert(eqTo(validORNRequest), eqTo(validPlrId), any[ObjectId]())
      }

      "fail with TaxObligationAlreadyFulfilled when a submission exists for the same accounting period" in {
        when(mockRepository.findByPillar2IdAndAccountingPeriod(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Some(ornMongoSubmission)))

        val result = service.submitORN(validPlrId, validORNRequest)

        whenReady(result.failed) { e =>
          e mustBe TaxObligationAlreadyFulfilled
        }
      }
    }

    "amendORN" should {
      "successfully amend an existing ORN when a submission exists" in {
        val amendedRequest = validORNRequest.copy(reportingEntityName = "Updated Name")

        when(mockRepository.findByPillar2IdAndAccountingPeriod(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Some(ornMongoSubmission)))
        when(mockRepository.insert(eqTo(validPlrId), eqTo(amendedRequest))).thenReturn(Future.successful(ObjectId.get()))
        when(mockOasRepository.insert(any[ORNRequest](), anyString(), any[ObjectId]())).thenReturn(Future.successful(true))

        val result = service.amendORN(validPlrId, amendedRequest)

        result.futureValue mustBe true
        verify(mockRepository, times(1)).insert(validPlrId, amendedRequest)
        verify(mockOasRepository).insert(eqTo(amendedRequest), eqTo(validPlrId), any[ObjectId]())
      }

      "fail with RequestCouldNotBeProcessed when no existing submission exists" in {
        when(mockRepository.findByPillar2IdAndAccountingPeriod(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))

        val result = service.amendORN(validPlrId, validORNRequest)

        whenReady(result.failed) { e =>
          e mustBe RequestCouldNotBeProcessed
        }
      }
    }

    "getORN" should {
      "return submission when it exists for the given period" in {
        val submission = ornMongoSubmission
        when(mockRepository.findByPillar2IdAndAccountingPeriod(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(Some(submission)))

        val result = service.getORN(validPlrId, submission.accountingPeriodFrom, submission.accountingPeriodTo)
        result.futureValue mustBe Some(submission)
      }

      "return None when no submission exists for the period" in {
        when(mockRepository.findByPillar2IdAndAccountingPeriod(anyString(), any[LocalDate], any[LocalDate]))
          .thenReturn(Future.successful(None))

        val result = service.getORN(validPlrId, LocalDate.now(), LocalDate.now())
        result.futureValue mustBe None
      }
    }
  }
}
