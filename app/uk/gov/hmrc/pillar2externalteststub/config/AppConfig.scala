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

package uk.gov.hmrc.pillar2externalteststub.config

import play.api.Configuration
import play.api.libs.json.{JsArray, Json}

import javax.inject.{Inject, Singleton}
import scala.io.Source

@Singleton
class AppConfig @Inject() (config: Configuration) {

  val appName:                 String = config.get[String]("appName")
  val defaultDataExpireInDays: Int    = config.get[Int]("defaultDataExpireInDays")
  lazy val isoCountryCodeList: String = config.get[String]("location.iso-country-code-list.all")

  lazy val countryList: Set[String] = {
    val source = Source.fromFile(isoCountryCodeList)
    try {
      val json = Json.parse(source.mkString)
      json.as[JsArray].value.map(countryEntry => (countryEntry \ 1).as[String].replace("country:", "")).toSet
    } finally source.close()
  }
}
