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

import play.api.libs.json.{JsError, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait DisposalDetailsAnswers extends Product with Serializable

object DisposalDetailsAnswers {

  final case class IncompleteDisposalDetailsAnswers(
    shareOfProperty: Option[ShareOfProperty],
    disposalPrice: Option[AmountInPence],
    disposalFees: Option[AmountInPence]
  ) extends DisposalDetailsAnswers

  final case class CompleteDisposalDetailsAnswers(
    shareOfProperty: ShareOfProperty,
    disposalPrice: AmountInPence,
    disposalFees: AmountInPence
  ) extends DisposalDetailsAnswers

  implicit val completeFormat: OFormat[CompleteDisposalDetailsAnswers]     = Json.format[CompleteDisposalDetailsAnswers]
  implicit val inCompleteFormat: OFormat[IncompleteDisposalDetailsAnswers] =
    Json.format[IncompleteDisposalDetailsAnswers]

  implicit val format: OFormat[DisposalDetailsAnswers] = new OFormat[DisposalDetailsAnswers] {
    override def reads(json: JsValue): JsResult[DisposalDetailsAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteDisposalDetailsAnswers", value) =>
            value.validate[IncompleteDisposalDetailsAnswers]
          case ("CompleteDisposalDetailsAnswers", value)   =>
            value.validate[CompleteDisposalDetailsAnswers]
          case (other, _)                                  =>
            JsError(s"Unrecognized DisposalDetailsAnswers type: $other")
        }
      case _                                    =>
        JsError("Expected DisposalDetailsAnswers wrapper object with a single entry")
    }

    override def writes(a: DisposalDetailsAnswers): JsObject = a match {
      case i: IncompleteDisposalDetailsAnswers =>
        Json.obj("IncompleteDisposalDetailsAnswers" -> Json.toJson(i))
      case c: CompleteDisposalDetailsAnswers   =>
        Json.obj("CompleteDisposalDetailsAnswers" -> Json.toJson(c))
    }
  }

}
