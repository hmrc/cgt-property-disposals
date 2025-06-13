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
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait ExampleCompanyDetailsAnswers extends Product with Serializable

object ExampleCompanyDetailsAnswers {

  final case class IncompleteExampleCompanyDetailsAnswers(
    address: Option[Address],
    disposalPrice: Option[AmountInPence],
    acquisitionPrice: Option[AmountInPence]
  ) extends ExampleCompanyDetailsAnswers

  final case class CompleteExampleCompanyDetailsAnswers(
    address: Address,
    disposalPrice: AmountInPence,
    acquisitionPrice: AmountInPence
  ) extends ExampleCompanyDetailsAnswers

  implicit val completeExampleCompanyDetailsAnswersFormat: OFormat[CompleteExampleCompanyDetailsAnswers]     =
    Json.format[CompleteExampleCompanyDetailsAnswers]
  implicit val incompleteExampleCompanyDetailsAnswersFormat: OFormat[IncompleteExampleCompanyDetailsAnswers] =
    Json.format[IncompleteExampleCompanyDetailsAnswers]

  implicit val format: OFormat[ExampleCompanyDetailsAnswers] = new OFormat[ExampleCompanyDetailsAnswers] {

    import play.api.libs.json._

    override def reads(json: JsValue): JsResult[ExampleCompanyDetailsAnswers] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("IncompleteExampleCompanyDetailsAnswers", value) =>
            value.validate[IncompleteExampleCompanyDetailsAnswers]
          case ("CompleteExampleCompanyDetailsAnswers", value)   => value.validate[CompleteExampleCompanyDetailsAnswers]
          case (other, _)                                        => JsError(s"Unrecognized ExampleCompanyDetailsAnswers type: $other")
        }
      case _                                    => JsError("Expected ExampleCompanyDetailsAnswers wrapper object with a single entry")
    }

    override def writes(o: ExampleCompanyDetailsAnswers): JsObject = o match {
      case i: IncompleteExampleCompanyDetailsAnswers =>
        Json.obj("IncompleteExampleCompanyDetailsAnswers" -> Json.toJson(i))
      case c: CompleteExampleCompanyDetailsAnswers   => Json.obj("CompleteExampleCompanyDetailsAnswers" -> Json.toJson(c))
    }
  }

}
