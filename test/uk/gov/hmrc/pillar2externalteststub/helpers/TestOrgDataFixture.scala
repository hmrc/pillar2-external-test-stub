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

import monocle.PLens
import monocle.macros.GenLens
import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.{Instant, LocalDate}

trait TestOrgDataFixture extends Pillar2DataFixture {

  implicit val mockOrgService: OrganisationService = mock[OrganisationService]

  val orgDetails: OrgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.of(2024, 1, 1)
  )

  val organisationDetails: TestOrganisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    accountStatus = AccountStatus(inactive = false),
    lastUpdated = java.time.Instant.parse("2024-01-01T00:00:00Z")
  )

  val testOrgDetails: TestOrganisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    accountStatus = AccountStatus(inactive = false),
    lastUpdated = Instant.now()
  )

  val nonDomesticOrganisation: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = nonDomesticPlrId,
    organisation = testOrgDetails
  )

  val domesticOrganisation: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = validPlrId,
    organisation = testOrgDetails.copy(orgDetails = testOrgDetails.orgDetails.copy(domesticOnly = true))
  )

  val organisationWithId: TestOrganisationWithId = organisationDetails.withPillar2Id(validPlrId)

  val testOrganisation: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = validPlrId,
    organisation = organisationDetails
  )

  val organisationWithActiveBtnFlag: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = "XEPLR0000000301",
    organisation = testOrgDetails.copy(accountStatus = AccountStatus(inactive = true))
  )

  val organisationWithInactiveBtnFlag: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = "XEPLR0000000302",
    organisation = testOrgDetails.copy(accountStatus = AccountStatus(inactive = false))
  )

  val nonDomesticOrganisationWithActiveBtnFlag: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = "XEPLR0000000303",
    organisation = testOrgDetails.copy(
      orgDetails = testOrgDetails.orgDetails.copy(domesticOnly = false),
      accountStatus = AccountStatus(inactive = true)
    )
  )

  val nonDomesticOrganisationWithInactiveBtnFlag: TestOrganisationWithId = TestOrganisationWithId(
    pillar2Id = "XEPLR0000000304",
    organisation = testOrgDetails.copy(
      orgDetails = testOrgDetails.orgDetails.copy(domesticOnly = false),
      accountStatus = AccountStatus(inactive = false)
    )
  )

  val configurableRegistrationDate: PLens[TestOrganisationWithId, TestOrganisationWithId, LocalDate, LocalDate] =
    GenLens[TestOrganisationWithId](_.organisation)
      .andThen(GenLens[TestOrganisation](_.orgDetails))
      .andThen(GenLens[OrgDetails](_.registrationDate))
}
