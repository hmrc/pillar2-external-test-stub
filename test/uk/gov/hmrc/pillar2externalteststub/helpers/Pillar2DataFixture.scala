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

package uk.gov.hmrc.pillar2externalteststub.helpers

import org.scalatest.Assertion
import org.scalatest.matchers.should.Matchers.*
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.*
import uk.gov.hmrc.pillar2externalteststub.models.organisation.AccountingPeriod

import java.time.LocalDate
import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.*

trait Pillar2DataFixture {

  extension [T](fut: Future[T]) {
    def shouldFailWith(expected: Throwable): Assertion = {
      val err = Await.result(fut.failed, 5.seconds)
      err shouldBe expected
    }
  }

  val authHeader: (String, String) = HeaderNames.authorisation -> "Bearer valid_token"
  val hipHeaders: Seq[(String, String)] = Seq(
    authHeader,
    correlationidHeader       -> UUID.randomUUID().toString,
    xReceiptDateHeader        -> nowZonedDateTime,
    xTransmittingSystemHeader -> "HIP",
    xOriginatingSystemHeader  -> "MDTP"
  )
  val accountActivityHeader: (String, String) = "X-Message-Type" -> "ACCOUNT_ACTIVITY"

  val validPlrId       = "XMPLR0000000000"
  val chargeReference  = "XM000000000000"
  val nonDomesticPlrId = "XEPLR1234567890"
  val serverErrorPlrId = "XEPLR5000000000"

  val accountingPeriod: AccountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31),
    None
  )
}
