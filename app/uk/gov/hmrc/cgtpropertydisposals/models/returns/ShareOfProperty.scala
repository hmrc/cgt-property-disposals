/*
 * Copyright 2020 HM Revenue & Customs
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

import cats.instances.bigDecimal._
import cats.syntax.eq._

import julienrf.json.derived
import play.api.libs.json.OFormat

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
    if (percentage === BigDecimal("50")) Half
    else if (percentage === BigDecimal("100")) Full
    else Other(percentage)

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val format: OFormat[ShareOfProperty] = derived.oformat()

}
