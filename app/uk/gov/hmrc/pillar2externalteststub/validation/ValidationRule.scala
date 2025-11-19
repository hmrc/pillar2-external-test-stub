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

package uk.gov.hmrc.pillar2externalteststub.validation

import cats.data.Validated.Invalid
import cats.data.Validated.Valid
import cats.implicits.*
import uk.gov.hmrc.pillar2externalteststub.validation.ValidationResult.*

sealed trait ValidationStrategy
case object AccumulateErrors extends ValidationStrategy
case object FailFast extends ValidationStrategy

trait ValidationRule[T] {
  def validate(value: T): ValidationResult[T]
}

object ValidationRule {
  def apply[T](f: T => ValidationResult[T]): ValidationRule[T] =
    (value: T) => f(value)

  def compose[T](rules: ValidationRule[T]*)(strategy: ValidationStrategy): ValidationRule[T] =
    new ValidationRule[T] {
      def validate(value: T): ValidationResult[T] =
        strategy match {
          case AccumulateErrors => validateAll(rules, value)
          case FailFast         => validateFirstFailure(rules, value)
        }
    }

  def validateAll[T](rules: Seq[ValidationRule[T]], value: T): ValidationResult[T] =
    rules.foldLeft(value.validNec[ValidationError]) { case (acc, rule) =>
      (acc, rule.validate(value)).mapN((_, _) => value)
    }

  def validateFirstFailure[T](rules: Seq[ValidationRule[T]], value: T): ValidationResult[T] =
    rules.foldLeft[ValidationResult[T]](value.validNec[ValidationError]) {
      case (Valid(_), rule)          => rule.validate(value)
      case (invalid @ Invalid(_), _) => invalid
    }
}
