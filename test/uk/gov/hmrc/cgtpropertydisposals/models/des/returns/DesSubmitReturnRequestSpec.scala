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
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
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

      for (a <- 1 to 1000) {
        val submitReturnRequest: SubmitReturnRequest =
          sample[SubmitReturnRequest].copy(completeReturn = sample[CompleteReturn]
            .copy(
              propertyAddress = sample[UkAddress],
              triageAnswers   = sample[CompleteSingleDisposalTriageAnswers]
            )
          )
        val ppdReturnDetails       = DesReturnDetails(submitReturnRequest)
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