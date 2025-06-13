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

import cats.kernel.Eq
import play.api.libs.json.{JsError, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait ExemptionAndLossesAnswers extends Product with Serializable

object ExemptionAndLossesAnswers {

  final case class IncompleteExemptionAndLossesAnswers(
    inYearLosses: Option[AmountInPence],
    previousYearsLosses: Option[AmountInPence],
    annualExemptAmount: Option[AmountInPence]
  ) extends ExemptionAndLossesAnswers

  final case class CompleteExemptionAndLossesAnswers(
    inYearLosses: AmountInPence,
    previousYearsLosses: AmountInPence,
    annualExemptAmount: AmountInPence
  ) extends ExemptionAndLossesAnswers

  implicit val eq: Eq[ExemptionAndLossesAnswers] = Eq.fromUniversalEquals

  implicit val completeFormat: OFormat[CompleteExemptionAndLossesAnswers]     =
    Json.format[CompleteExemptionAndLossesAnswers]
  implicit val inCompleteFormat: OFormat[IncompleteExemptionAndLossesAnswers] =
    Json.format[IncompleteExemptionAndLossesAnswers]

  implicit val format: OFormat[ExemptionAndLossesAnswers] = new OFormat[ExemptionAndLossesAnswers] {
    override def reads(json: JsValue): JsResult[ExemptionAndLossesAnswers] =
      json match {
        case JsObject(fields) if fields.size == 1 =>
          fields.head match {
            case ("IncompleteExemptionAndLossesAnswers", value) =>
              value.validate[IncompleteExemptionAndLossesAnswers]
            case ("CompleteExemptionAndLossesAnswers", value)   =>
              value.validate[CompleteExemptionAndLossesAnswers]
            case (other, _)                                     =>
              JsError(s"Unrecognized ExemptionAndLossesAnswers type: $other")
          }
        case _                                    =>
          JsError("Expected ExemptionAndLossesAnswers wrapper object with a single entry")
      }

    override def writes(e: ExemptionAndLossesAnswers): JsObject = e match {
      case i: IncompleteExemptionAndLossesAnswers =>
        Json.obj("IncompleteExemptionAndLossesAnswers" -> Json.toJson(i))
      case c: CompleteExemptionAndLossesAnswers   =>
        Json.obj("CompleteExemptionAndLossesAnswers" -> Json.toJson(c))
    }
  }

}
