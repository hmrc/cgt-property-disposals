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

import play.api.libs.json.*
import scala.CanEqual.derived
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait AcquisitionDetailsAnswers extends Product with Serializable

object AcquisitionDetailsAnswers {

  final case class IncompleteAcquisitionDetailsAnswers(
    acquisitionMethod: Option[AcquisitionMethod],
    acquisitionDate: Option[AcquisitionDate],
    acquisitionPrice: Option[AmountInPence],
    rebasedAcquisitionPrice: Option[AmountInPence],
    improvementCosts: Option[AmountInPence],
    acquisitionFees: Option[AmountInPence],
    shouldUseRebase: Option[Boolean]
  ) extends AcquisitionDetailsAnswers

  final case class CompleteAcquisitionDetailsAnswers(
    acquisitionMethod: AcquisitionMethod,
    acquisitionDate: AcquisitionDate,
    acquisitionPrice: AmountInPence,
    rebasedAcquisitionPrice: Option[AmountInPence],
    improvementCosts: AmountInPence,
    acquisitionFees: AmountInPence,
    shouldUseRebase: Boolean
  ) extends AcquisitionDetailsAnswers

  implicit val completeFormat: OFormat[CompleteAcquisitionDetailsAnswers]     =
    Json.format[CompleteAcquisitionDetailsAnswers]
  implicit val inCompleteFormat: OFormat[IncompleteAcquisitionDetailsAnswers] =
    Json.format[IncompleteAcquisitionDetailsAnswers]

  implicit val format: OFormat[AcquisitionDetailsAnswers] = new OFormat[AcquisitionDetailsAnswers] {
    override def reads(json: JsValue): JsResult[AcquisitionDetailsAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteAcquisitionDetailsAnswers", value) =>
            value.validate[IncompleteAcquisitionDetailsAnswers]
          case ("CompleteAcquisitionDetailsAnswers", value)   =>
            value.validate[CompleteAcquisitionDetailsAnswers]
          case (other, _)                                     =>
            JsError(s"Unrecognized AcquisitionDetailsAnswers type: $other")
        }
      case _                                    =>
        JsError("Expected AcquisitionDetailsAnswers wrapper object with a single entry")
    }

    override def writes(a: AcquisitionDetailsAnswers): JsObject = a match {
      case i: IncompleteAcquisitionDetailsAnswers =>
        Json.obj("IncompleteAcquisitionDetailsAnswers" -> Json.toJson(i))
      case c: CompleteAcquisitionDetailsAnswers   =>
        Json.obj("CompleteAcquisitionDetailsAnswers" -> Json.toJson(c))
    }
  }

}
