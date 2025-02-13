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

import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesAcquisitionType

sealed trait AcquisitionMethod extends Product with Serializable

object AcquisitionMethod {

  final case object Bought extends AcquisitionMethod

  final case object Inherited extends AcquisitionMethod

  final case object Gifted extends AcquisitionMethod

  final case class Other(value: String) extends AcquisitionMethod

  def apply(desAcquisitionType: DesAcquisitionType): AcquisitionMethod =
    desAcquisitionType match {
      case DesAcquisitionType.Bought       => Bought
      case DesAcquisitionType.Inherited    => Inherited
      case DesAcquisitionType.Gifted       => Gifted
      case DesAcquisitionType.Other(value) => Other(value)
    }

  implicit val format: OFormat[AcquisitionMethod] = derived.oformat()

}
