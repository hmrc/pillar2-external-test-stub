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

package uk.gov.hmrc.pillar2externalteststub.models.error

sealed trait StubError extends Exception {
  def code:    String
  def message: String
  override def getMessage: String = message
}

case object InvalidJson extends StubError {
  override val code:    String = "INVALID_JSON"
  override val message: String = "Invalid JSON payload provided"
}

case object EmptyRequestBody extends StubError {
  override val code:    String = "EMPTY_REQUEST_BODY"
  override val message: String = "Empty request body provided"
}

case class MissingHeader(headerName: String) extends StubError {
  override val code:    String = "MISSING_HEADER"
  override val message: String = s"Required header '$headerName' is missing"
}

case class OrganisationAlreadyExists(pillar2Id: String) extends StubError {
  override val code:    String = "ORGANISATION_EXISTS"
  override val message: String = s"Organisation with pillar2Id: $pillar2Id already exists"
}

case class OrganisationNotFound(pillar2Id: String) extends StubError {
  override val code:    String = "ORGANISATION_NOT_FOUND"
  override val message: String = s"No organisation found with pillar2Id: $pillar2Id"
}

case class DatabaseError(error: String) extends StubError {
  override val code:    String = "DATABASE_ERROR"
  override val message: String = s"Database operation failed: $error"
}
