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

import play.api.libs.json.Json
import play.api.mvc.Results._
import play.api.mvc.Result
import uk.gov.hmrc.pillar2externalteststub.models.TestScenarios

object TestScenarioHelper {
  def processTestScenario(scenario: String, validScenarios: Set[String]): Option[Result] = {
    if (!validScenarios.contains(scenario)) {
      Some(BadRequest(Json.obj(
        "code" -> "INVALID_TEST_SCENARIO",
        "message" -> s"Test scenario '$scenario' is not valid for this endpoint"
      )))
    } else {
      TestScenarios.findByCode(scenario).map(_.response)
    }
  }
} 