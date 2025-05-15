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
import java.time.temporal.ChronoUnit
import java.time.{ZoneOffset, ZonedDateTime}
import scala.util.Random
import scala.util.matching.Regex

object Pillar2Helper {
  val pillar2Regex: Regex = "^[A-Z0-9]{1,15}$".r
  val ServerErrorPlrId          = "XEPLR5000000000"
  val correlationidHeader       = "correlationid"
  val correlationidHeaderRegex  = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"
  val xReceiptDateHeaderRegex   = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"
  val xReceiptDateHeader        = "X-Receipt-Date"
  val xOriginatingSystemHeader  = "X-Originating-System"
  val xTransmittingSystemHeader = "X-Transmitting-System"
  val MAX_NO_SUBMISSIONS:                         Int  = 10
  val AMENDMENT_WINDOW_MONTHS:                    Long = 12
  val FIRST_AP_DUE_DATE_FROM_REGISTRATION_MONTHS: Long = 18

  def nowZonedDateTime:           String = ZonedDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS).toString
  def generateFormBundleNumber(): String = f"${Random.nextLong(1000000000000L) % 1000000000000L}%012d"
  def generateChargeReference(): String = {
    val letters = Random.alphanumeric.filter(_.isLetter).map(_.toUpper).take(2).mkString
    val digits  = f"${Random.nextLong(1000000000000L) % 1000000000000L}%012d"
    s"$letters$digits"
  }
}
