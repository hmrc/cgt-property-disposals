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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionMethod

sealed trait DesAcquisitionType extends Product with Serializable

object DesAcquisitionType {

  case object Bought extends DesAcquisitionType

  case object Inherited extends DesAcquisitionType

  case object Gifted extends DesAcquisitionType

  final case class Other(value: String) extends DesAcquisitionType

  def apply(m: AcquisitionMethod): DesAcquisitionType =
    m match {
      case AcquisitionMethod.Bought       => Bought
      case AcquisitionMethod.Inherited    => Inherited
      case AcquisitionMethod.Gifted       => Gifted
      case AcquisitionMethod.Other(value) => Other(value)
    }

  implicit val format: Format[DesAcquisitionType] = new Format[DesAcquisitionType] {
    override def reads(json: JsValue): JsResult[DesAcquisitionType] = json match {
      case JsString("bought")    => JsSuccess(Bought)
      case JsString("inherited") => JsSuccess(Inherited)
      case JsString("gifted")    => JsSuccess(Gifted)
      case JsString(other)       => JsSuccess(Other(other))
      case other                 => JsError(s"Expected string for acquisition type but got $other")
    }

    override def writes(o: DesAcquisitionType): JsValue = o match {
      case Bought       => JsString("bought")
      case Inherited    => JsString("inherited")
      case Gifted       => JsString("gifted")
      case Other(other) => JsString(other)
    }
  }

}
