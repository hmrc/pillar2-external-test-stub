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

import enumeratum.EnumEntry.UpperSnakecase
import enumeratum.{Enum, EnumEntry, PlayJsonEnum}
import play.api.libs.json.{Json, OFormat, Reads}
import uk.gov.hmrc.pillar2externalteststub.models.common.MonetaryReads

sealed trait Liabilities

case class Liability(
  electionDTTSingleMember:  Boolean,
  electionUTPRSingleMember: Boolean,
  numberSubGroupDTT:        Int,
  numberSubGroupUTPR:       Int,
  totalLiability:           BigDecimal,
  totalLiabilityDTT:        BigDecimal,
  totalLiabilityIIR:        BigDecimal,
  totalLiabilityUTPR:       BigDecimal,
  liableEntities:           Seq[LiableEntity]
) extends Liabilities

object Liability {

  implicit val monetaryReads: Reads[BigDecimal]  = MonetaryReads.monetaryValueReads
  implicit val format:        OFormat[Liability] = Json.format[Liability]
}

case class LiabilityNilReturn(returnType: ReturnType) extends Liabilities

object LiabilityNilReturn {
  implicit val liabilityNilReturnFormat: OFormat[LiabilityNilReturn] = Json.format[LiabilityNilReturn]
}

case class LiableEntity(
  ukChargeableEntityName: String,
  idType:                 String,
  idValue:                String,
  amountOwedDTT:          BigDecimal,
  amountOwedIIR:          BigDecimal,
  amountOwedUTPR:         BigDecimal
)

object LiableEntity {
  implicit val monetaryReads: Reads[BigDecimal]     = MonetaryReads.monetaryValueReads
  implicit val format:        OFormat[LiableEntity] = Json.format[LiableEntity]
}

sealed trait ReturnType extends EnumEntry with UpperSnakecase

object ReturnType extends Enum[ReturnType] with PlayJsonEnum[ReturnType] {
  val values: IndexedSeq[ReturnType] = findValues
  case object NIL_RETURN extends ReturnType
}
