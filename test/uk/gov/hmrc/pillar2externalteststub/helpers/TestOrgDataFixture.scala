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

import org.scalatestplus.mockito.MockitoSugar.mock
import uk.gov.hmrc.pillar2externalteststub.models.organisation._
import uk.gov.hmrc.pillar2externalteststub.services.OrganisationService

import java.time.LocalDate

trait TestOrgDataFixture extends Pillar2DataFixture {

  val mockOrgService: OrganisationService = mock[OrganisationService]

  val orgDetails: OrgDetails = OrgDetails(
    domesticOnly = false,
    organisationName = "Test Org",
    registrationDate = LocalDate.of(2024, 1, 1)
  )

  val organisationDetails: TestOrganisation = TestOrganisation(
    orgDetails = orgDetails,
    accountingPeriod = accountingPeriod,
    lastUpdated = java.time.Instant.parse("2024-01-01T00:00:00Z")
  )

  val organisationWithId: TestOrganisationWithId = organisationDetails.withPillar2Id(validPlrId)
}
