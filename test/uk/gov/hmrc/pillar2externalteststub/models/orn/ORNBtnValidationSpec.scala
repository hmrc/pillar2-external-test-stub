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

package uk.gov.hmrc.pillar2externalteststub.models.orn

import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.{ORNDataFixture, TestOrgDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.RequestCouldNotBeProcessed
import uk.gov.hmrc.pillar2externalteststub.validation.syntax.*

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ORNBtnValidationSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with ORNDataFixture with TestOrgDataFixture {

  "BTN Status Validation" should {

    "Scenario 1: ORN create with BTN flag active (inactive = true)" should {
      "return error 003 'Request could not be processed'" in {

        when(mockOrgService.getOrganisation(organisationWithActiveBtnFlag.pillar2Id))
          .thenReturn(Future.successful(organisationWithActiveBtnFlag))

        val result = ORNValidator.ornValidator(organisationWithActiveBtnFlag.pillar2Id)(mockOrgService, global).flatMap { validator =>
          Future.successful(validORNRequest.validate(using validator))
        }

        whenReady(result) { validationResult =>
          validationResult.isInvalid mustBe true
          validationResult.fold(
            errors => {
              errors.head mustBe a[ORNValidationError]
              val error = errors.head.asInstanceOf[ORNValidationError]
              error.error mustBe RequestCouldNotBeProcessed
              error.errorCode mustBe "003"
              error.errorMessage mustBe "Request could not be processed"
            },
            _ => fail("Expected validation to fail")
          )
        }
      }
    }

    "Scenario 2: ORN amend with BTN flag active (inactive = true)" should {
      "return error 003 'Request could not be processed'" in {

        when(mockOrgService.getOrganisation(organisationWithActiveBtnFlag.pillar2Id))
          .thenReturn(Future.successful(organisationWithActiveBtnFlag))

        val result = ORNValidator.ornValidator(organisationWithActiveBtnFlag.pillar2Id)(mockOrgService, global).flatMap { validator =>
          Future.successful(validORNRequest.validate(using validator))
        }

        whenReady(result) { validationResult =>
          validationResult.isInvalid mustBe true
          validationResult.fold(
            errors => {
              errors.head mustBe a[ORNValidationError]
              val error = errors.head.asInstanceOf[ORNValidationError]
              error.error mustBe RequestCouldNotBeProcessed
              error.errorCode mustBe "003"
            },
            _ => fail("Expected validation to fail")
          )
        }
      }
    }

    "Scenario 3: ORN create with BTN flag inactive (inactive = false)" should {
      "return success and allow submission" in {
        when(mockOrgService.getOrganisation(nonDomesticOrganisationWithInactiveBtnFlag.pillar2Id))
          .thenReturn(Future.successful(nonDomesticOrganisationWithInactiveBtnFlag))

        val result = ORNValidator.ornValidator(nonDomesticOrganisationWithInactiveBtnFlag.pillar2Id)(mockOrgService, global).flatMap { validator =>
          Future.successful(validORNRequest.validate(using validator))
        }

        whenReady(result) { validationResult =>
          validationResult.isValid mustBe true
        }
      }
    }

    "Scenario 4: ORN amend with BTN flag inactive (inactive = false)" should {
      "return success and allow amendment" in {

        when(mockOrgService.getOrganisation(nonDomesticOrganisationWithInactiveBtnFlag.pillar2Id))
          .thenReturn(Future.successful(nonDomesticOrganisationWithInactiveBtnFlag))

        val result = ORNValidator.ornValidator(nonDomesticOrganisationWithInactiveBtnFlag.pillar2Id)(mockOrgService, global).flatMap { validator =>
          Future.successful(validORNRequest.validate(using validator))
        }

        whenReady(result) { validationResult =>
          validationResult.isValid mustBe true
        }
      }
    }

    "BTN status rule in isolation" should {
      "return error when BTN flag is active" in {
        val rule             = ORNValidationRules.btnStatusRule(organisationWithActiveBtnFlag)
        val validationResult = validORNRequest.validate(using rule)

        validationResult.isInvalid mustBe true
        validationResult.fold(
          errors => {
            val error = errors.head.asInstanceOf[ORNValidationError]
            error.error mustBe RequestCouldNotBeProcessed
          },
          _ => fail("Expected validation to fail")
        )
      }

      "return success when BTN flag is inactive" in {
        val rule             = ORNValidationRules.btnStatusRule(organisationWithInactiveBtnFlag)
        val validationResult = validORNRequest.validate(using rule)

        validationResult.isValid mustBe true
      }
    }
  }
}
