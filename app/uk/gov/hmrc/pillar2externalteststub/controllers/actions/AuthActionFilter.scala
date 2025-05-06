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
import uk.gov.hmrc.pillar2externalteststub.models.error.ETMPError.ETMPBadRequest

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class AuthActionFilter @Inject() ()(implicit ec: ExecutionContext) extends ActionFilter[Request] {

  override def filter[A](request: Request[A]): Future[Option[Result]] = {
    def validateHeader(header: String, validationFn: String => Boolean): Future[Option[Nothing]] =
      if (request.headers.get(header).exists(validationFn)) Future.successful(None)
      else {
        logger.error(s"Header is missing or invalid: $header")
        throw ETMPBadRequest(s"Header is missing or invalid: $header")
      }

    validateHeader(correlationidHeader, _.matches(correlationidHeaderRegex))
    validateHeader(xReceiptDateHeader, _.matches(xReceiptDateHeaderRegex))
    validateHeader(xOriginatingSystemHeader, _.equals("MDTP"))
    validateHeader(xTransmittingSystemHeader, _.equals("HIP"))

    request.headers.get(HeaderNames.authorisation) match {
      case Some(_) => Future.successful(None)
      case _       => Future.successful(Some(Unauthorized))
    }
  }

  override def executionContext: ExecutionContext = ec
}
