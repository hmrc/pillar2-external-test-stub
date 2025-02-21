/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.pillar2externalteststub.repositories

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.organisation._

import java.time.LocalDate
import scala.concurrent.ExecutionContext.Implicits.global

class OrganisationRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[TestOrganisationWithId]
    with ScalaFutures
    with IntegrationPatience {

  override protected val databaseName: String = "test-organisation-repository"

  val config = new AppConfig(
    Configuration.from(
      Map(
        "appName"                 -> "pillar2-external-test-stub",
        "defaultDataExpireInDays" -> 28
      )
    )
  )

  override protected val repository: OrganisationRepository =
    new OrganisationRepository(mongoComponent, config)

  private val orgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.of(2024, 1, 1)
  )

  private val accountingPeriod = AccountingPeriod(
    startDate = LocalDate.of(2024, 1, 1),
    endDate = LocalDate.of(2024, 12, 31)
  )

  private val organisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    lastUpdated = java.time.Instant.parse("2024-01-01T00:00:00Z")
  )

  private val organisationWithId = TestOrganisationWithId("TEST123", organisation)

  "insert" should {
    "successfully insert a new organisation" in {
      val result = repository.insert(organisationWithId).futureValue
      result shouldBe true

      val retrieved = repository.findByPillar2Id("TEST123").futureValue
      retrieved shouldBe Some(organisationWithId)
    }

    "fail with DatabaseError when inserting a duplicate pillar2Id" in {
      repository.insert(organisationWithId).futureValue shouldBe true

      val error = repository.insert(organisationWithId).failed.futureValue
      error          shouldBe a[DatabaseError]
      error.getMessage should include("Failed to create organisation")
    }
  }

  "findByPillar2Id" should {
    "return None when no organisation exists" in {
      repository.findByPillar2Id("NONEXISTENT").futureValue shouldBe None
    }

    "return the organisation when it exists" in {
      repository.insert(organisationWithId).futureValue shouldBe true
      val result = repository.findByPillar2Id("TEST123").futureValue
      result shouldBe Some(organisationWithId)
    }
  }

  "update" should {
    "update an existing organisation" in {
      repository.insert(organisationWithId).futureValue shouldBe true

      val updatedOrganisation = organisation.copy(
        orgDetails = orgDetails.copy(organisationName = "Updated Org")
      )
      val updatedWithId = TestOrganisationWithId("TEST123", updatedOrganisation)

      repository.update(updatedWithId).futureValue shouldBe true

      val retrieved = repository.findByPillar2Id("TEST123").futureValue
      retrieved shouldBe Some(updatedWithId)
    }

    "insert a new organisation if it doesn't exist" in {
      repository.update(organisationWithId).futureValue shouldBe true
      repository.findByPillar2Id("TEST123").futureValue shouldBe Some(organisationWithId)
    }
  }

  "delete" should {
    "successfully delete an existing organisation" in {
      repository.insert(organisationWithId).futureValue shouldBe true
      repository.delete("TEST123").futureValue          shouldBe true
      repository.findByPillar2Id("TEST123").futureValue shouldBe None
    }

    "return true when deleting a non-existent organisation" in {
      repository.delete("NONEXISTENT").futureValue shouldBe true
    }
  }
}
