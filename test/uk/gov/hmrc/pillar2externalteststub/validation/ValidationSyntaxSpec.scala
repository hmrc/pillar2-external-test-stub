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

import cats.data.NonEmptyChain
import org.scalatest.matchers.must.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.pillar2externalteststub.validation.models.TestValidationError._

import java.time.LocalDate

import ValidationResult._

case class TestLiability(
  entityName: String,
  amount:     BigDecimal
)

case class TestSubmission(
  periodStart: LocalDate,
  periodEnd:   LocalDate,
  liabilities: List[TestLiability]
)

object TestSubmission {
  val dateValidation: ValidationRule[TestSubmission] = ValidationRule[TestSubmission] { submission =>
    if (submission.periodEnd.isAfter(submission.periodStart)) valid(submission)
    else invalid(InvalidDateRange("/periodDates", "End date must be after start date"))
  }

  val liabilityValidation: ValidationRule[TestSubmission] = ValidationRule[TestSubmission] { submission =>
    if (submission.liabilities.nonEmpty) valid(submission)
    else invalid(MandatoryFieldMissing("/liabilities"))
  }

  val amountValidation: ValidationRule[TestSubmission] = ValidationRule[TestSubmission] { submission =>
    val invalidAmounts = submission.liabilities.zipWithIndex.collect {
      case (liability, index) if liability.amount < 0 =>
        InvalidAmount(s"/liabilities/$index/amount", liability.amount)
    }
    if (invalidAmounts.isEmpty) valid(submission)
    else invalidNec(NonEmptyChain.fromSeq(invalidAmounts).get)
  }

  implicit val validator: ValidationRule[TestSubmission] =
    ValidationRule.compose(dateValidation, liabilityValidation, amountValidation)(AccumulateErrors)
}

class ValidationSyntaxSpec extends AnyWordSpec with Matchers {
  import syntax._

  "ValidationSyntax" should {
    "validate a correct submission" in {
      val submission = TestSubmission(
        periodStart = LocalDate.of(2024, 1, 1),
        periodEnd = LocalDate.of(2024, 12, 31),
        liabilities = List(
          TestLiability("Entity 1", 1000),
          TestLiability("Entity 2", 2000)
        )
      )

      val result = submission.validate
      result.isValid mustBe true
    }

    "fail validation for invalid dates" in {
      val submission = TestSubmission(
        periodStart = LocalDate.of(2024, 12, 31),
        periodEnd = LocalDate.of(2024, 1, 1),
        liabilities = List(TestLiability("Entity 1", 1000))
      )

      val result = submission.validate
      result.isInvalid mustBe true
      result.toEither match {
        case Left(errors) => errors.head mustBe a[InvalidDateRange]
        case Right(_)     => fail("Expected validation to fail")
      }
    }

    "accumulate multiple validation errors by default" in {
      val submission = TestSubmission(
        periodStart = LocalDate.of(2024, 12, 31),
        periodEnd = LocalDate.of(2024, 1, 1),
        liabilities = List(
          TestLiability("Entity 1", -1000),
          TestLiability("Entity 2", -2000)
        )
      )

      val result = submission.validate
      result.isInvalid mustBe true
      result.toEither match {
        case Left(errors) =>
          errors.length mustBe 3
          errors.collect { case e: InvalidDateRange => e }.length mustBe 1
          errors.collect { case e: InvalidAmount => e }.length mustBe 2
        case Right(_) => fail("Expected validation to fail")
      }
    }

    "use fail-fast validation when configured" in {
      val failFastValidator = ValidationRule.compose(
        TestSubmission.dateValidation,
        TestSubmission.liabilityValidation,
        TestSubmission.amountValidation
      )(FailFast)

      val submission = TestSubmission(
        periodStart = LocalDate.of(2024, 12, 31),
        periodEnd = LocalDate.of(2024, 1, 1),
        liabilities = List(
          TestLiability("Entity 1", -1000),
          TestLiability("Entity 2", -2000)
        )
      )

      val result = submission.validate(failFastValidator)
      result.isInvalid mustBe true
      result.toEither match {
        case Left(errors) =>
          errors.length mustBe 1
          errors.head mustBe a[InvalidDateRange]
        case Right(_) => fail("Expected validation to fail")
      }
    }
  }
}
