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

package uk.gov.hmrc.pillar2externalteststub.controllers

import org.scalatest.Inspectors.forAll
import org.scalatest.OptionValues
import org.scalatest.freespec.AnyFreeSpec
import org.scalatest.matchers.must.Matchers.convertToAnyMustWrapper
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status.CREATED
import play.api.libs.json.{JsObject, Json}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRDataFixture
import uk.gov.hmrc.pillar2externalteststub.helpers.UKTRHelper._
import uk.gov.hmrc.pillar2externalteststub.models.uktr.UKTRErrorCodes

import java.time.ZonedDateTime

class SubmitUKTRControllerSpec extends AnyFreeSpec with Matchers with GuiceOneAppPerSuite with OptionValues with UKTRDataFixture {

  def request(plrReference: String = domesticOnlyPlrReference, body: JsObject): FakeRequest[JsObject] =
    FakeRequest(POST, routes.SubmitUKTRController.submitUKTR.url)
      .withHeaders("Content-Type" -> "application/json", authHeader, "X-Pillar2-Id" -> plrReference)
      .withBody(body)

  "when subscription cannot be fetched" - {
    "422 response should be returned" in {
      val result = route(app, request(plrReference = "XEPLR0123456500", body = validRequestBody)).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      (contentAsJson(result) \ "errors" \ "code").as[String] mustEqual "007"
    }
  }

  "when pillar2Id is missing" - {
    "a 422 should be returned" in {
      val missingPlrIdRequest = FakeRequest(POST, routes.SubmitUKTRController.submitUKTR.url)
        .withHeaders("Content-Type" -> "application/json", authHeader)
        .withBody(validRequestBody)
      val result = route(app, missingPlrIdRequest).value
      status(result) mustBe UNPROCESSABLE_ENTITY
      (contentAsJson(result) \ "errors" \ "code").as[String] mustEqual "002"
    }
  }

  "when invalid JSON is submitted" - {
    "a 400 should be returned" in {
      val result = route(app, request(body = Json.obj("invalid" -> true))).value
      status(result) mustBe BAD_REQUEST
      (contentAsJson(result) \ "error" \ "code").as[String] mustEqual "400"
    }
  }

  "when submitting a Liability UKTR" - {
    "should return CREATED (201)" - {
      "when plrReference is valid and JSON payload is correct" in {
        val result = route(app, request(body = validRequestBody)).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
      }

      "when plrReference is valid and JSON is correct and has 3 Liable Entities" in {
        val result = route(
          app,
          request(body =
            validRequestBody.deepMerge(
              Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity, validLiableEntity, validLiableEntity)))
            )
          )
        ).value
        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
        (json \ "success" \ "chargeReference").as[String] mustBe "XTC01234123412"
      }
    }

    "should return UNPROCESSABLE_ENTITY (422)" - {
      "when totalLiability is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiability" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_096
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiability must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityDTT is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityDTT" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_DTT_098
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityDTT must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityIIR is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityIIR" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_IIR_097
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when totalLiabilityUTPR is invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result =
            route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("totalLiabilityUTPR" -> amount))))).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_TOTAL_LIABILITY_UTPR_099
          (json \ "errors" \ "text")
            .as[String] mustBe "totalLiabilityUTPR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when liabilityEntity is invalid" in {
        val result = route(app, request(body = validRequestBody.deepMerge(Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr()))))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
        (json \ "errors" \ "text").as[String] mustBe "liabilityEntity cannot be empty"
      }

      "when ukChargeableEntityName is Invalid" in {
        val result = route(app, request(body = invalidUkChargeableEntityNameRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when ukChargeableEntityName exceeds 160 characters" in {
        val result = route(app, request(body = ukChargeableEntityNameTooLongRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when ukChargeableEntityName is Empty in 3rd LiableEntity" in {
        val result = route(
          app,
          request(body =
            validRequestBody.deepMerge(
              Json.obj(
                "liabilities" -> Json.obj(
                  "liableEntities" -> Json.arr(validLiableEntity, validLiableEntity, validLiableEntity ++ Json.obj("ukChargeableEntityName" -> ""))
                )
              )
            )
          )
        ).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "ukChargeableEntityName must have a minimum length of 1 and a maximum length of 160."
      }

      "when idType has zero length" in {
        val result = route(app, request(body = invalidIdTypeZeroLengthRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idType is Invalid" in {
        val result = route(app, request(body = invalidIdTypeRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idType in LiableEntity1 is Invalid and idValue in LiableEntity2 is Invalid" in {
        val result = route(app, request(body = invalidIdTypeEntity1AndInvalidIdValueEntity2RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idType must be either UTR or CRN."
      }

      "when idValue has zero length" in {
        val result = route(app, request(body = invalidIdValueZeroLengthRequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
      }

      "when idValue length exceeds 15 characters" in {
        val result = route(app, request(body = invalidIdValueLengthExceeds15RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text").as[String] mustBe "idValue must be alphanumeric, and have a minimum length of 1 and a maximum length of 15."
      }

      "when amountOwedDTT is Invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedDTT" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (json \ "errors" \ "text")
            .as[String] mustBe "amountOwedDTT must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedIIR is Invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedIIR" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (json \ "errors" \ "text")
            .as[String] mustBe "amountOwedIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedUTPR is Invalid" in {
        forAll(invalidUKTRAmounts) { amount =>
          val result = route(
            app,
            request(body =
              validRequestBody.deepMerge(
                Json.obj("liabilities" -> Json.obj("liableEntities" -> Json.arr(validLiableEntity ++ Json.obj("amountOwedUTPR" -> amount))))
              )
            )
          ).value

          status(result) mustBe UNPROCESSABLE_ENTITY
          val json = contentAsJson(result)
          (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
          (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
          (json \ "errors" \ "text")
            .as[String] mustBe "amountOwedUTPR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
        }
      }

      "when amountOwedIIR is Invalid in LiableEntity2 and amountOwedUTPR is Invalid in LiableEntity3" in {
        val result = route(app, request(body = invalidAmountOwedIIREntity2AndInvalidAmountOwedUTPREntity3RequestBody)).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.REQUEST_COULD_NOT_BE_PROCESSED_003
        (json \ "errors" \ "text")
          .as[String] mustBe "amountOwedIIR must be a number between 0 and 9999999999999.99 with up to 2 decimal places"
      }
    }

    "should return BAD_REQUEST (400)" - {
      "when ukChargeableEntityName is missing" in {
        val result = route(app, request(body = missingUkChargeableEntityNameRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when ukChargeableEntityName is missing and invalidLiableEntityukChargeableEntityNameZeroLength" in {
        val result = route(app, request(body = missingUkChargeableEntNameLiableEntity2AndInvalidIdTypeLiableEnt3ReqBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when idType is missing" in {
        val result = route(app, request(body = missingIdTypeRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when idValue is missing" in {
        val result = route(app, request(body = missingIdValueRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedDTT is missing" in {
        val result = route(app, request(body = missingAmountOwedDTTRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }

      "when amountOwedIIR is missing" in {
        val result = route(app, request(body = missingAmountOwedIIRRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when amountOwedUTPR is missing" in {
        val result = route(app, request(body = missingAmountOwedUTPRRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
    }

    "should return INTERNAL_SERVER_ERROR (500)" - {
      "when plrReference indicates SAP failure" in {
        val result = route(app, request(plrReference = ServerErrorPlrId, body = validRequestBody)).value

        status(result) mustBe INTERNAL_SERVER_ERROR
        contentAsJson(result) mustBe Json.obj(
          "error" -> Json.obj(
            "code"    -> "500",
            "message" -> "Internal server error",
            "logID"   -> "C0000000000000000000000000000500"
          )
        )
      }
    }
  }

  "when submitting a nil UKTR" - {
    "should return CREATED (201)" - {
      "when submitting a Domestic-Only Nil Return with electionUKGAAP = true" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a Domestic-Only Nil Return with electionUKGAAP = false" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a Non-Domestic Nil Return with electionUKGAAP = false" in {
        val result =
          route(app, request(plrReference = PlrId, body = nilReturnBody(obligationMTT = true, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a domestic-only Nil Return with obligationMTT = false" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = false, electionUKGAAP = true))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a non-domestic Nil Return with obligationMTT = false" in {
        val result =
          route(app, request(plrReference = PlrId, body = nilReturnBody(obligationMTT = false, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }

      "when submitting a non-domestic Nil Return with obligationMTT = true" in {
        val result =
          route(app, request(plrReference = PlrId, body = nilReturnBody(obligationMTT = true, electionUKGAAP = false))).value

        status(result) mustBe CREATED
        val json = contentAsJson(result)
        (json \ "success" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "success" \ "formBundleNumber").as[String] mustBe "119000004320"
      }
    }

    "should return UNPROCESSABLE_ENTITY (422)" - {
      "when submitting a domestic-only Nil Return with obligationMTT = true" in {
        val result = route(app, request(body = nilReturnBody(obligationMTT = true, electionUKGAAP = true))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[ZonedDateTime].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
        (json \ "errors" \ "text").as[String] mustBe "obligationMTT cannot be true for a domestic-only group"
      }

      "when submitting a Non-Domestic Nil Return with electionUKGAAP = true" in {
        val result =
          route(app, request(plrReference = PlrId, body = nilReturnBody(obligationMTT = true, electionUKGAAP = true))).value

        status(result) mustBe UNPROCESSABLE_ENTITY
        val json = contentAsJson(result)
        (json \ "errors" \ "processingDate").asOpt[String].isDefined mustBe true
        (json \ "errors" \ "code").as[String] mustBe UKTRErrorCodes.INVALID_RETURN_093
        (json \ "errors" \ "text").as[String] mustBe "electionUKGAAP can be true only for a domestic-only group"
      }
    }

    "should return BAD_REQUEST (400)" - {
      "when NilReturn AccountingPeriodFrom date is invalid" in {
        val result = route(app, request(body = invalidAccountingPeriodFromNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodFrom date is missing" in {
        val result = route(app, request(body = missingAccountingPeriodFromNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodTo date is invalid" in {
        val result = route(app, request(body = invalidAccountingPeriodToNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn AccountingPeriodTo date is missing" in {
        val result = route(app, request(body = missingAccountingPeriodToNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ObligationMTT field is invalid" in {
        val result = route(app, request(body = invalidObligationMTTNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ObligationMTT field is missing" in {
        val result = route(app, request(body = missingObligationMTTNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ElectionUKGAAP field is invalid" in {
        val result = route(app, request(body = invalidElectionUKGAAPNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn ElectionUKGAAP field is missing" in {
        val result = route(app, request(body = missingElectionUKGAAPNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn returnType is invalid" in {
        val result = route(app, request(body = invalidReturnTypeNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
      "when NilReturn returnType is empty" in {
        val result = route(app, request(body = emptyReturnTypeNilReturnRequestBody)).value

        status(result) mustBe BAD_REQUEST
      }
    }
  }
}
