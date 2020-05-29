/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import com.eclipsesource.schema.drafts.Version7
import com.eclipsesource.schema.{SchemaType, SchemaValidator}
import org.scalatest.WordSpec
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.NINO
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AssetType.IndirectDisposal
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, RepresenteeContactDetails, RepresenteeDetails, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.util.JsErrorOps._

import scala.io.Source

class DesSubmitReturnRequestSpec extends WordSpec {

  "DesSubmitReturnRequest" must {

    "have a method which transforms submit requests correctly" in {
      import Version7._
      val schemaToBeValidated = Json
        .fromJson[SchemaType](
          Json.parse(
            Source
              .fromInputStream(
                this.getClass.getResourceAsStream("/resources/submit-return-des-schema-v-1-0-0.json")
              )
              .mkString
          )
        )
        .get

      val validator = SchemaValidator(Some(Version7))

      for (_ <- 1 to 1000) {
        val submitReturnRequest: SubmitReturnRequest =
          sample[SubmitReturnRequest].copy(completeReturn =
            sample[CompleteReturn]
              .fold[CompleteReturn](
                _.copy(
                  examplePropertyDetailsAnswers =
                    sample[CompleteExamplePropertyDetailsAnswers].copy(address = sample[UkAddress]),
                  triageAnswers = sample[CompleteMultipleDisposalsTriageAnswers]
                ),
                _.copy(
                  propertyAddress = sample[UkAddress],
                  triageAnswers = sample[CompleteSingleDisposalTriageAnswers]
                ),
                _.copy(
                  companyAddress = sample[NonUkAddress],
                  triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(assetType = IndirectDisposal)
                )
              )
          )

        val representeeDetails = sampleOptional[RepresenteeDetails].map(
          _.copy(
            answers = sample[CompleteRepresenteeAnswers].copy(
              contactDetails = sample[RepresenteeContactDetails].copy(
                address = sample[UkAddress],
                emailAddress = Email("email@email.com")
              )
            ),
            id = Right(Left(NINO("AB123456C")))
          )
        )

        val ppdReturnDetails       = DesReturnDetails(submitReturnRequest, representeeDetails)
        val desSubmitReturnRequest = DesSubmitReturnRequest(ppdReturnDetails)
        val json                   = Json.toJson(desSubmitReturnRequest)
        val validationResult       = validator.validate(schemaToBeValidated, json)

        withClue(s"****** Validating json ******" + json.toString() + "******") {
          validationResult match {
            case JsSuccess(_, _) => ()
            case error: JsError  => fail(s"Test failed due to validation failure ${error.prettyPrint()} ")
          }
        }
      }
    }

  }

}
