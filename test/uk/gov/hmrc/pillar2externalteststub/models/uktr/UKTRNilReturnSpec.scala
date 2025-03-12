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

package uk.gov.hmrc.pillar2externalteststub.models.uktr

import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import play.api.libs.json.Json
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import org.scalatestplus.mockito.MockitoSugar
import scala.concurrent.ExecutionContext.Implicits.global

class UKTRNilReturnSpec extends AnyFreeSpec with Matchers with UKTRDataFixture with MockitoSugar {

  private val mockOrgService = mock[OrganisationService]

  "UKTRNilReturn validation" - {
    val validNilReturn = Json.fromJson[UKTRNilReturn](nilReturnBody(obligationMTT = false, electionUKGAAP = false)).get
    "should pass validation for a valid nil return" in {
      val result = UKTRNilReturn.uktrNilReturnValidator("validPlrId")(mockOrgService, global).map(_.validate(validNilReturn))
      result mustEqual valid(validNilReturn)
    }

    "should fail validation when obligationMTT is true for domestic organization" in {
      val invalidReturn = validNilReturn.copy(
        obligationMTT = true
      )
      val result = UKTRNilReturn.uktrNilReturnValidator("validPlrId")(mockOrgService, global).map(_.validate(invalidReturn))
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when electionUKGAAP is true for non-domestic organization" in {
      val invalidReturn = validNilReturn.copy(
        electionUKGAAP = true
      )
      val result = UKTRNilReturn.uktrNilReturnValidator("validPlrId")(mockOrgService, global).map(_.validate(invalidReturn))
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }

    "should fail validation when accounting period doesn't match organization's" in {
      val invalidReturn = validNilReturn.copy(
        accountingPeriodFrom = validNilReturn.accountingPeriodFrom.plusDays(1),
        accountingPeriodTo = validNilReturn.accountingPeriodTo.plusDays(1)
      )
      val result = UKTRNilReturn.uktrNilReturnValidator("validPlrId")(mockOrgService, global).map(_.validate(invalidReturn))
      result mustEqual invalid(UKTRSubmissionError(InvalidReturn))
    }
  }
} 

