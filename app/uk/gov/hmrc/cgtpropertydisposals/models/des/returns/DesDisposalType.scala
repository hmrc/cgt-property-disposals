/*
 * Copyright 2022 HM Revenue & Customs
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
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalMethod

sealed trait DesDisposalType extends Product with Serializable

object DesDisposalType {

  case object Sold extends DesDisposalType

  case object Gifted extends DesDisposalType

  case object Other extends DesDisposalType

  def apply(m: DisposalMethod): DesDisposalType =
    m match {
      case DisposalMethod.Sold   => Sold
      case DisposalMethod.Gifted => Gifted
      case DisposalMethod.Other  => Other

    }

  implicit val format: Format[DesDisposalType] =
    Format(
      { json: JsValue =>
        json match {
          case JsString("sold")   => JsSuccess(Sold)
          case JsString("gifted") => JsSuccess(Gifted)
          case JsString("other")  => JsSuccess(Other)
          case JsString(other)    => JsError(s"Could not parse disposal type $other")
          case other              => JsError(s"Expected string for acquisition type but got $other")
        }
      },
      { disposalType: DesDisposalType =>
        disposalType match {
          case Sold   => JsString("sold")
          case Gifted => JsString("gifted")
          case Other  => JsString("other")
        }
      }
    )

}
