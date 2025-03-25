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

package uk.gov.hmrc.pillar2externalteststub

import play.api.i18n.Lang.logger
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper.{ServerErrorPlrId, pillar2Regex}
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.{ETMPInternalServerError, IdMissingOrInvalid}

import scala.concurrent.Future

package object controllers {

  private[controllers] def validatePillar2Id(pillar2Id: Option[String]): Future[String] =
    pillar2Id match {
      case Some(ServerErrorPlrId) =>
        logger.warn("Server error triggered by special PLR ID")
        Future.failed(ETMPInternalServerError)
      case Some(id) if pillar2Regex.matches(id) =>
        logger.info(s"Valid Pillar2Id received: $id")
        Future.successful(id)
      case other =>
        logger.warn(s"Invalid Pillar2Id received: $other")
        Future.failed(IdMissingOrInvalid)
    }
}
