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

import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError._
import uk.gov.hmrc.pillar2externalteststub.models.error.OrganisationNotFound
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.{invalid, valid}
import uk.gov.hmrc.pillar2externalteststub.validation.{ValidationError, ValidationRule}

import scala.concurrent.{ExecutionContext, Future}

case class ORNValidationError(error: ETMPError) extends ValidationError {
  override def errorCode:    String = error.code
  override def errorMessage: String = error.message
  override def field:        String = "ORNRequest"
}

object ORNValidationRules {

  // Validation rule to check if the group is domestic only
  def domesticOnlyRule(
    pillar2Id:                    String
  )(implicit organisationService: OrganisationService, ec: ExecutionContext): Future[ValidationRule[ORNRequest]] =
    organisationService
      .getOrganisation(pillar2Id)
      .map { org =>
        val isDomesticOnly = org.organisation.orgDetails.domesticOnly
        ValidationRule[ORNRequest] { request =>
          if (isDomesticOnly) {
            invalid(ORNValidationError(RequestCouldNotBeProcessed))
          } else {
            valid(request)
          }
        }
      }
      .recover { case _: OrganisationNotFound =>
        ValidationRule[ORNRequest](_ => invalid(ORNValidationError(NoActiveSubscription)))
      }
}
