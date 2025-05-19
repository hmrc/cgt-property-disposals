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
import uk.gov.hmrc.cgtpropertydisposals.models.name.IndividualName

sealed trait RepresenteeAnswers extends Product with Serializable

object RepresenteeAnswers {

  final case class IncompleteRepresenteeAnswers(
    name: Option[IndividualName],
    id: Option[RepresenteeReferenceId],
    dateOfDeath: Option[DateOfDeath],
    contactDetails: Option[RepresenteeContactDetails],
    hasConfirmedPerson: Boolean,
    hasConfirmedContactDetails: Boolean,
    isFirstReturn: Option[Boolean]
  ) extends RepresenteeAnswers

  final case class CompleteRepresenteeAnswers(
    name: IndividualName,
    id: RepresenteeReferenceId,
    dateOfDeath: Option[DateOfDeath],
    contactDetails: RepresenteeContactDetails,
    isFirstReturn: Boolean
  ) extends RepresenteeAnswers

  implicit val completeFormat: OFormat[CompleteRepresenteeAnswers]     = Json.format[CompleteRepresenteeAnswers]
  implicit val incompleteFormat: OFormat[IncompleteRepresenteeAnswers] = Json.format[IncompleteRepresenteeAnswers]

  implicit val format: OFormat[RepresenteeAnswers] = new OFormat[RepresenteeAnswers] {
    override def reads(json: JsValue): JsResult[RepresenteeAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteRepresenteeAnswers", value) => value.validate[IncompleteRepresenteeAnswers]
          case ("CompleteRepresenteeAnswers", value)   => value.validate[CompleteRepresenteeAnswers]
          case (other, _)                              => JsError(s"Unrecognized RepresenteeAnswers type: $other")
        }
      case _                                    => JsError("Expected RepresenteeAnswers wrapper object with a single entry")
    }

    override def writes(r: RepresenteeAnswers): JsObject = r match {
      case i: IncompleteRepresenteeAnswers => Json.obj("IncompleteRepresenteeAnswers" -> Json.toJson(i))
      case c: CompleteRepresenteeAnswers   => Json.obj("CompleteRepresenteeAnswers" -> Json.toJson(c))
    }
  }

}
