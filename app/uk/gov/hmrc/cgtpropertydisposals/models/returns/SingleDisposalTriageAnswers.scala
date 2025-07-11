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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.{Json, OFormat}

import uk.gov.hmrc.cgtpropertydisposals.models.address.Country

sealed trait SingleDisposalTriageAnswers extends Product with Serializable

object SingleDisposalTriageAnswers {

  final case class IncompleteSingleDisposalTriageAnswers(
    individualUserType: Option[IndividualUserType],
    hasConfirmedSingleDisposal: Boolean,
    disposalMethod: Option[DisposalMethod],
    wasAUKResident: Option[Boolean],
    countryOfResidence: Option[Country],
    assetType: Option[AssetType],
    disposalDate: Option[DisposalDate],
    alreadySentSelfAssessment: Option[Boolean],
    completionDate: Option[CompletionDate]
  ) extends SingleDisposalTriageAnswers

  final case class CompleteSingleDisposalTriageAnswers(
    individualUserType: Option[IndividualUserType],
    disposalMethod: DisposalMethod,
    countryOfResidence: Country,
    assetType: AssetType,
    disposalDate: DisposalDate,
    alreadySentSelfAssessment: Option[Boolean],
    completionDate: CompletionDate
  ) extends SingleDisposalTriageAnswers

  implicit val completeFormat: OFormat[CompleteSingleDisposalTriageAnswers]     =
    Json.format[CompleteSingleDisposalTriageAnswers]
  implicit val inCompleteFormat: OFormat[IncompleteSingleDisposalTriageAnswers] =
    Json.format[IncompleteSingleDisposalTriageAnswers]

  implicit val format: OFormat[SingleDisposalTriageAnswers] = new OFormat[SingleDisposalTriageAnswers] {

    import play.api.libs.json._

    override def reads(json: JsValue): JsResult[SingleDisposalTriageAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteSingleDisposalTriageAnswers", value) =>
            value.validate[IncompleteSingleDisposalTriageAnswers]
          case ("CompleteSingleDisposalTriageAnswers", value)   =>
            value.validate[CompleteSingleDisposalTriageAnswers]
          case (other, _)                                       =>
            JsError(s"Unrecognized SingleDisposalTriageAnswers type: $other")
        }
      case _                                    =>
        JsError("Expected SingleDisposalTriageAnswers wrapper object with a single entry")
    }

    override def writes(o: SingleDisposalTriageAnswers): JsObject = o match {
      case i: IncompleteSingleDisposalTriageAnswers =>
        Json.obj("IncompleteSingleDisposalTriageAnswers" -> Json.toJson(i))
      case c: CompleteSingleDisposalTriageAnswers   =>
        Json.obj("CompleteSingleDisposalTriageAnswers" -> Json.toJson(c))
    }
  }

}
