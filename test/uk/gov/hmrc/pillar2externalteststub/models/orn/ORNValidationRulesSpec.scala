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

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.pillar2externalteststub.helpers.ORNDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.TestOrgDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{NoActiveSubscription, RequestCouldNotBeProcessed}
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.validation.syntax._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class ORNValidationRulesSpec extends AnyWordSpec with Matchers with MockitoSugar with ScalaFutures with ORNDataFixture with TestOrgDataFixture {

  "ORNValidationRules" should {
    "domesticOnlyRule" should {
      "reject domestic-only organisations" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(domesticOrganisation))

        val result = ORNValidationRules.domesticOnlyRule(validPlrId)(mockOrgService, global).flatMap { rule =>
          Future.successful(validORNRequest.validate(rule))
        }

        whenReady(result) { validationResult =>
          validationResult.isInvalid mustBe true
          validationResult.toEither.left.map { errors =>
            errors.head.asInstanceOf[ORNValidationError].error mustBe RequestCouldNotBeProcessed
          }
        }
      }

      "allow non-domestic organisations" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.successful(nonDomesticOrganisation))

        val result = ORNValidationRules.domesticOnlyRule(validPlrId)(mockOrgService, global).flatMap { rule =>
          Future.successful(validORNRequest.validate(rule))
        }

        whenReady(result) { validationResult =>
          validationResult.isValid mustBe true
        }
      }

      "return NoActiveSubscription when organisation not found" in {
        when(mockOrgService.getOrganisation(anyString())).thenReturn(Future.failed(OrganisationNotFound(validPlrId)))

        val result = ORNValidationRules.domesticOnlyRule(validPlrId)(mockOrgService, global).flatMap { rule =>
          Future.successful(validORNRequest.validate(rule))
        }

        whenReady(result) { validationResult =>
          validationResult.isInvalid mustBe true
          validationResult.toEither.left.map { errors =>
            errors.head.asInstanceOf[ORNValidationError].error mustBe NoActiveSubscription
          }
        }
      }
    }
  }
}
