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

package uk.gov.hmrc.pillar2externalteststub.controllers.actions

import play.api.i18n.Lang.logger
import play.api.mvc.Results.Unauthorized
import play.api.mvc.{ActionFilter, Request, Result}
import uk.gov.hmrc.http.HeaderNames
import uk.gov.hmrc.pillar2externalteststub.helpers.Pillar2Helper._
import uk.gov.hmrc.pillar2externalteststub.models.error.HIPBadRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthActionFilter @Inject() ()(implicit ec: ExecutionContext) extends ActionFilter[Request] {

  override def filter[A](request: Request[A]): Future[Option[Result]] = {
    def validateHeader(header: String, validationFn: String => Boolean): Future[Unit] =
      if (request.headers.get(header).exists(validationFn)) Future.successful(())
      else {
        logger.error(s"Header is missing or invalid: $header")
        Future.failed(HIPBadRequest(s"Header is missing or invalid: $header"))
      }

    for {
      _ <- validateHeader(correlationidHeader, _.matches(correlationidHeaderRegex))
      _ <- validateHeader(xReceiptDateHeader, _.matches(xReceiptDateHeaderRegex))
      _ <- validateHeader(xOriginatingSystemHeader, _.equals("MDTP"))
      _ <- validateHeader(xTransmittingSystemHeader, _.equals("HIP"))
      authResult <- {
        if (request.headers.get(HeaderNames.authorisation).isDefined) Future.successful(None)
        else Future.successful(Some(Unauthorized))
      }
    } yield authResult
  }

  override def executionContext: ExecutionContext = ec
}
