/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.{JsError, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country

sealed trait MultipleDisposalsTriageAnswers

object MultipleDisposalsTriageAnswers {

  final case class IncompleteMultipleDisposalsTriageAnswers(
    individualUserType: Option[IndividualUserType],
    numberOfProperties: Option[Int],
    wasAUKResident: Option[Boolean],
    countryOfResidence: Option[Country],
    wereAllPropertiesResidential: Option[Boolean],
    assetTypes: Option[List[AssetType]],
    taxYearExchanged: Option[TaxYearExchanged],
    taxYear: Option[TaxYear],
    alreadySentSelfAssessment: Option[Boolean],
    completionDate: Option[CompletionDate]
  ) extends MultipleDisposalsTriageAnswers

  final case class CompleteMultipleDisposalsTriageAnswers(
    individualUserType: Option[IndividualUserType],
    numberOfProperties: Int,
    countryOfResidence: Country,
    assetTypes: List[AssetType],
    taxYearExchanged: Option[TaxYearExchanged],
    taxYear: TaxYear,
    alreadySentSelfAssessment: Option[Boolean],
    completionDate: CompletionDate
  ) extends MultipleDisposalsTriageAnswers

  implicit val completeMultipleDisposalsTriageAnswersFormat: OFormat[CompleteMultipleDisposalsTriageAnswers]     =
    Json.format[CompleteMultipleDisposalsTriageAnswers]
  implicit val incompleteMultipleDisposalsTriageAnswersFormat: OFormat[IncompleteMultipleDisposalsTriageAnswers] =
    Json.format[IncompleteMultipleDisposalsTriageAnswers]

  implicit val format: OFormat[MultipleDisposalsTriageAnswers] = new OFormat[MultipleDisposalsTriageAnswers] {
    override def reads(json: JsValue): JsResult[MultipleDisposalsTriageAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteMultipleDisposalsTriageAnswers", value) =>
            value.validate[IncompleteMultipleDisposalsTriageAnswers]
          case ("CompleteMultipleDisposalsTriageAnswers", value)   =>
            value.validate[CompleteMultipleDisposalsTriageAnswers]
          case (other, _)                                          =>
            JsError(s"Unrecognized MultipleDisposalsTriageAnswers type: $other")
        }
      case _                                    =>
        JsError("Expected wrapper object with one MultipleDisposalsTriageAnswers entry")
    }

    override def writes(o: MultipleDisposalsTriageAnswers): JsObject = o match {
      case i: IncompleteMultipleDisposalsTriageAnswers =>
        Json.obj("IncompleteMultipleDisposalsTriageAnswers" -> Json.toJson(i))
      case c: CompleteMultipleDisposalsTriageAnswers   =>
        Json.obj("CompleteMultipleDisposalsTriageAnswers" -> Json.toJson(c))
    }
  }
}
