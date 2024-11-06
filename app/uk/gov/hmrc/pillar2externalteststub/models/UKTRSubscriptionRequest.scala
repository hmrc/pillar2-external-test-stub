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

package uk.gov.hmrc.pillar2externalteststub.models

import play.api.libs.json.{Json, OWrites}

case class UKTRSubscriptionRequest(
  accountingPeriodFrom: String,
  accountingPeriodTo:   String,
  qualifyingGroup:      Boolean,
  obligationDTT:        Boolean,
  obligationMTT:        Boolean,
  liabilities:          Liabilities
)

object UKTRSubscriptionRequest {
  implicit val writes: OWrites[UKTRSubscriptionRequest] = Json.writes[UKTRSubscriptionRequest]
}

case class Liabilities(
  totalLiability:     BigDecimal,
  totalLiabilityDTT:  BigDecimal,
  totalLiabilityIIR:  BigDecimal,
  totalLiabilityUTPR: BigDecimal,
  liableEntities:     Seq[LiableEntity]
)

object Liabilities {
  implicit val writes: OWrites[Liabilities] = Json.writes[Liabilities]
}

case class LiableEntity(
  ukChargeableEntityName: String,
  idType:                 String,
  idValue:                String,
  amountOwedDTT:          BigDecimal,
  electedDTT:             Boolean,
  amountOwedIIR:          BigDecimal,
  amountOwedUTPR:         BigDecimal,
  electedUTPR:            Boolean
)

object LiableEntity {
  implicit val writes: OWrites[LiableEntity] = Json.writes[LiableEntity]
}