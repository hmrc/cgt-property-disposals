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
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait ReliefDetailsAnswers extends Product with Serializable

object ReliefDetailsAnswers {

  final case class IncompleteReliefDetailsAnswers(
    privateResidentsRelief: Option[AmountInPence],
    lettingsRelief: Option[AmountInPence],
    otherReliefs: Option[OtherReliefsOption]
  ) extends ReliefDetailsAnswers

  final case class CompleteReliefDetailsAnswers(
    privateResidentsRelief: AmountInPence,
    lettingsRelief: AmountInPence,
    otherReliefs: Option[OtherReliefsOption]
  ) extends ReliefDetailsAnswers

  implicit val completeFormat: OFormat[CompleteReliefDetailsAnswers]     = Json.format[CompleteReliefDetailsAnswers]
  implicit val inCompleteFormat: OFormat[IncompleteReliefDetailsAnswers] = Json.format[IncompleteReliefDetailsAnswers]

  implicit val format: OFormat[ReliefDetailsAnswers] = new OFormat[ReliefDetailsAnswers] {
    import play.api.libs.json._
    override def reads(json: JsValue): JsResult[ReliefDetailsAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteReliefDetailsAnswers", value) =>
            value.validate[IncompleteReliefDetailsAnswers]
          case ("CompleteReliefDetailsAnswers", value)   =>
            value.validate[CompleteReliefDetailsAnswers]
          case (other, _)                                =>
            JsError(s"Unrecognized ReliefDetailsAnswers type: $other")
        }
      case _                                    =>
        JsError("Expected ReliefDetailsAnswers wrapper object with a single entry")
    }

    override def writes(a: ReliefDetailsAnswers): JsObject = a match {
      case i: IncompleteReliefDetailsAnswers =>
        Json.obj("IncompleteReliefDetailsAnswers" -> Json.toJson(i))
      case c: CompleteReliefDetailsAnswers   =>
        Json.obj("CompleteReliefDetailsAnswers" -> Json.toJson(c))
    }
  }
}
