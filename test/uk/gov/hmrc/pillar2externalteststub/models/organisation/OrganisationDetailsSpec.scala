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

package uk.gov.hmrc.pillar2externalteststub.models.organisation

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsSuccess, Json}

import java.time.{Instant, LocalDate}

class OrganisationDetailsSpec extends AnyWordSpec with Matchers {

  "OrgDetails" should {
    "serialize to JSON correctly" in {
      val orgDetails = OrgDetails(
        domesticOnly = false,
        organisationName = "Test Org",
        registrationDate = LocalDate.of(2024, 1, 1)
      )

      val json = Json.toJson(orgDetails)
      json.toString should include("Test Org")
      json.toString should include("2024-01-01")
      json.toString should include("false")
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse("""
        {
          "domesticOnly": false,
          "organisationName": "Test Org",
          "registrationDate": "2024-01-01"
        }
      """)

      json.validate[OrgDetails]            shouldBe a[JsSuccess[_]]
      json.as[OrgDetails].organisationName shouldBe "Test Org"
    }
  }

  "AccountingPeriod" should {
    "serialize to JSON correctly" in {
      val period = AccountingPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        duetDate = LocalDate.of(2024, 4, 6)
      )

      val json = Json.toJson(period)
      json.toString should include("2024-01-01")
      json.toString should include("2024-12-31")
      json.toString should include("2024-04-06")
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse("""
        {
          "startDate": "2024-01-01",
          "endDate": "2024-12-31",
          "duetDate": "2024-04-06"
        }
      """)

      json.validate[AccountingPeriod]     shouldBe a[JsSuccess[_]]
      json.as[AccountingPeriod].startDate shouldBe LocalDate.of(2024, 1, 1)
    }
  }

  "OrganisationDetails" should {
    val orgDetails = OrgDetails(
      domesticOnly = false,
      organisationName = "Test Org",
      registrationDate = LocalDate.of(2024, 1, 1)
    )

    val accountingPeriod = AccountingPeriod(
      startDate = LocalDate.of(2024, 1, 1),
      endDate = LocalDate.of(2024, 12, 31),
      duetDate = LocalDate.of(2024, 4, 6)
    )

    val fixedInstant = Instant.parse("2024-01-01T00:00:00Z")
    val organisationDetails = OrganisationDetails(
      orgDetails = orgDetails,
      accountingPeriod = accountingPeriod,
      lastUpdated = fixedInstant
    )

    "serialize to JSON correctly" in {
      val json = Json.toJson(organisationDetails)(OrganisationDetails.mongoFormat)
      json.toString should include("Test Org")
      json.toString should include("2024-01-01")
      json.toString should include(""""$date":{"$numberLong":"1704067200000"}""")
    }

    "deserialize from JSON correctly" in {
      val json = Json.parse("""
        {
          "orgDetails": {
            "domesticOnly": false,
            "organisationName": "Test Org",
            "registrationDate": "2024-01-01"
          },
          "accountingPeriod": {
            "startDate": "2024-01-01",
            "endDate": "2024-12-31",
            "duetDate": "2024-04-06"
          },
          "lastUpdated": {
            "$date": {
              "$numberLong": "1704067200000"
            }
          }
        }
      """)

      json.validate[OrganisationDetails](OrganisationDetails.mongoFormat) shouldBe a[JsSuccess[_]]
      val parsed = json.as[OrganisationDetails](OrganisationDetails.mongoFormat)
      parsed.orgDetails.organisationName shouldBe "Test Org"
      parsed.lastUpdated                 shouldBe fixedInstant
    }

    "create OrganisationDetailsWithId correctly" in {
      val withId = organisationDetails.withPillar2Id("TEST123")
      withId.pillar2Id shouldBe "TEST123"
      withId.details   shouldBe organisationDetails
    }
  }
}
