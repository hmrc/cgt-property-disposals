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

import cats.instances.bigDecimal.*
import cats.syntax.eq.*
import play.api.libs.json.*

sealed trait ShareOfProperty extends Product with Serializable {
  val percentageValue: BigDecimal
}

object ShareOfProperty {

  case object Full extends ShareOfProperty {
    val percentageValue: BigDecimal = BigDecimal("100")
  }

  case object Half extends ShareOfProperty {
    val percentageValue: BigDecimal = BigDecimal("50")
  }

  final case class Other(percentageValue: BigDecimal) extends ShareOfProperty

  def apply(percentage: BigDecimal): ShareOfProperty =
    if percentage === BigDecimal("50") then Half
    else if percentage === BigDecimal("100") then Full
    else Other(percentage)

  implicit val format: Format[ShareOfProperty] = new Format[ShareOfProperty] {
    override def reads(json: JsValue): JsResult[ShareOfProperty] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head match {
          case ("Full", _)  => JsSuccess(Full)
          case ("Half", _)  => JsSuccess(Half)
          case ("Other", v) => (v \ "percentageValue").validate[BigDecimal].map(Other(_))
          case (other, _)   => JsError(s"Unrecognized ShareOfProperty type: $other")
        }
      case _                                    => JsError("Expected ShareOfProperty JSON object with one key")
    }

    override def writes(o: ShareOfProperty): JsValue = o match {
      case Full         => Json.obj("Full" -> Json.obj())
      case Half         => Json.obj("Half" -> Json.obj())
      case Other(value) => Json.obj("Other" -> Json.obj("percentageValue" -> value))
    }
  }

}
