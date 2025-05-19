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

import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait MixedUsePropertyDetailsAnswers extends Product with Serializable

object MixedUsePropertyDetailsAnswers {

  final case class IncompleteMixedUsePropertyDetailsAnswers(
    address: Option[UkAddress],
    disposalPrice: Option[AmountInPence],
    acquisitionPrice: Option[AmountInPence]
  ) extends MixedUsePropertyDetailsAnswers

  object IncompleteMixedUsePropertyDetailsAnswers {
    val empty: IncompleteMixedUsePropertyDetailsAnswers =
      IncompleteMixedUsePropertyDetailsAnswers(None, None, None)

    def fromCompleteAnswers(
      c: CompleteMixedUsePropertyDetailsAnswers
    ): IncompleteMixedUsePropertyDetailsAnswers =
      IncompleteMixedUsePropertyDetailsAnswers(
        Some(c.address),
        Some(c.disposalPrice),
        Some(c.acquisitionPrice)
      )

  }

  final case class CompleteMixedUsePropertyDetailsAnswers(
    address: UkAddress,
    disposalPrice: AmountInPence,
    acquisitionPrice: AmountInPence
  ) extends MixedUsePropertyDetailsAnswers

  implicit val ukAddressFormat: OFormat[UkAddress]                                 = Json.format
  implicit val completeFormat: OFormat[CompleteMixedUsePropertyDetailsAnswers]     =
    Json.format[CompleteMixedUsePropertyDetailsAnswers]
  implicit val incompleteFormat: OFormat[IncompleteMixedUsePropertyDetailsAnswers] =
    Json.format[IncompleteMixedUsePropertyDetailsAnswers]

  implicit val format: OFormat[MixedUsePropertyDetailsAnswers] = new OFormat[MixedUsePropertyDetailsAnswers] {
    override def reads(json: play.api.libs.json.JsValue): play.api.libs.json.JsResult[MixedUsePropertyDetailsAnswers] =
      json match {
        case play.api.libs.json.JsObject(fields) if fields.size == 1 =>
          fields.head match {
            case ("IncompleteMixedUsePropertyDetailsAnswers", value) =>
              value.validate[IncompleteMixedUsePropertyDetailsAnswers]
            case ("CompleteMixedUsePropertyDetailsAnswers", value)   =>
              value.validate[CompleteMixedUsePropertyDetailsAnswers]
            case (other, _)                                          =>
              play.api.libs.json.JsError(s"Unrecognized MixedUsePropertyDetailsAnswers type: $other")
          }
        case _                                                       =>
          play.api.libs.json.JsError("Expected wrapper object with a single MixedUsePropertyDetailsAnswers entry")
      }

    override def writes(o: MixedUsePropertyDetailsAnswers): play.api.libs.json.JsObject = o match {
      case i: IncompleteMixedUsePropertyDetailsAnswers =>
        play.api.libs.json.Json.obj("IncompleteMixedUsePropertyDetailsAnswers" -> play.api.libs.json.Json.toJson(i))
      case c: CompleteMixedUsePropertyDetailsAnswers   =>
        play.api.libs.json.Json.obj("CompleteMixedUsePropertyDetailsAnswers" -> play.api.libs.json.Json.toJson(c))
    }
  }

}
