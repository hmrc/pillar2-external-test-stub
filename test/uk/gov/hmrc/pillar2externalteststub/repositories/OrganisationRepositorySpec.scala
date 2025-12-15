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

package uk.gov.hmrc.pillar2externalteststub.repositories

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.Configuration
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.pillar2externalteststub.config.AppConfig
import uk.gov.hmrc.pillar2externalteststub.helpers.{BTNDataFixture, TestOrgDataFixture, UKTRDataFixture}
import uk.gov.hmrc.pillar2externalteststub.models.error.DatabaseError
import uk.gov.hmrc.pillar2externalteststub.models.organisation.*

class OrganisationRepositorySpec
    extends AnyWordSpec
    with Matchers
    with DefaultPlayMongoRepositorySupport[TestOrganisationWithId]
    with ScalaFutures
    with IntegrationPatience
    with UKTRDataFixture
    with BTNDataFixture
    with TestOrgDataFixture
    with MockitoSugar {

  override protected val databaseName: String = "test-organisation-repository"

  val config = new AppConfig(
    Configuration.from(
      Map(
        "appName"                 -> "pillar2-external-test-stub",
        "defaultDataExpireInDays" -> 28
      )
    )
  )

  private val app = GuiceApplicationBuilder()
    .configure(
      "metrics.enabled"  -> false,
      "encryptionToggle" -> "true"
    )
    .overrides(
      bind[MongoComponent].toInstance(mongoComponent)
    )
    .build()

  override protected val repository: OrganisationRepository =
    app.injector.instanceOf[OrganisationRepository]

  private val organisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    testData = Some(TestData(AccountActivityScenario.SOLE_CHARGE)),
    accountStatus = AccountStatus(inactive = false),
    lastUpdated = java.time.Instant.parse("2024-01-01T00:00:00Z")
  )

  "insert" should {
    "successfully insert a new organisation" in {
      val result = repository.insert(organisationWithId).futureValue
      result shouldBe true

      val retrieved = repository.findByPillar2Id(validPlrId).futureValue
      retrieved shouldBe Some(organisationWithId)
    }

    "fail to insert a duplicate pillar2Id" in {
      repository.insert(organisationWithId).futureValue shouldBe true

      whenReady(repository.insert(organisationWithId).failed) { exception =>
        exception shouldBe a[DatabaseError]
      }
    }
  }

  "findByPillar2Id" should {
    "return None when no organisation exists" in {
      repository.findByPillar2Id("NONEXISTENT").futureValue shouldBe None
    }

    "return the organisation when it exists" in {
      repository.insert(organisationWithId).futureValue  shouldBe true
      repository.findByPillar2Id(validPlrId).futureValue shouldBe Some(organisationWithId)
    }
  }

  "update" should {
    "update an existing organisation" in {
      repository.insert(organisationWithId).futureValue shouldBe true

      val updatedOrganisation = organisation.copy(
        orgDetails = orgDetails.copy(organisationName = "Updated Org")
      )
      val updatedWithId = updatedOrganisation.withPillar2Id(validPlrId)

      repository.update(updatedWithId).futureValue shouldBe true

      val retrieved = repository.findByPillar2Id(validPlrId).futureValue
      retrieved shouldBe Some(updatedWithId)
    }

    "insert a new organisation if it doesn't exist" in {
      repository.update(organisationWithId).futureValue  shouldBe true
      repository.findByPillar2Id(validPlrId).futureValue shouldBe Some(organisationWithId)
    }
  }

  "delete" should {
    "successfully delete an organisation and its associated submissions" in {
      val btnRepository:  BTNSubmissionRepository  = app.injector.instanceOf[BTNSubmissionRepository]
      val uktrRepository: UKTRSubmissionRepository = app.injector.instanceOf[UKTRSubmissionRepository]
      btnRepository.ensureIndexes().futureValue
      uktrRepository.ensureIndexes().futureValue
      repository.insert(organisationWithId).futureValue
      btnRepository.insert(validPlrId, validBTNRequest).futureValue
      uktrRepository.insert(nilSubmission, validPlrId).futureValue

      repository.delete(validPlrId).futureValue

      repository.findByPillar2Id(validPlrId).futureValue     shouldBe empty
      btnRepository.findByPillar2Id(validPlrId).futureValue  shouldBe empty
      uktrRepository.findByPillar2Id(validPlrId).futureValue shouldBe empty
    }

    "return true when deleting a non-existent organisation" in {
      repository.delete("NONEXISTENT").futureValue shouldBe true
    }
  }
}
