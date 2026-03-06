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

package uk.gov.hmrc.pillar2externalteststub.models.orn

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.{ORNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.repositories.ORNSubmissionRepository
import uk.gov.hmrc.pillar2externalteststub.validation.syntax.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ORNValidatorSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with ORNDataFixture with TestOrgDataFixture {

  private val mockRepository = mock[ORNSubmissionRepository]

  "ORNValidator" should {
    "validate successfully for valid data" in {
      when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))
      when(mockRepository.findByPillar2Id(anyString())).thenReturn(Future.successful(Seq.empty))

      val result = ORNValidator.ornValidator(validPlrId)(mockOrgService, global).flatMap { validator =>
        Future.successful(validORNRequest.validate(using validator))
      }

      whenReady(result) { validationResult =>
        validationResult.isValid mustBe true
      }
    }
  }
}
