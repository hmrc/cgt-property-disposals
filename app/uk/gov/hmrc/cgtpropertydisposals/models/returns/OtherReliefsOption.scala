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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

sealed trait OtherReliefsOption extends Product with Serializable

object OtherReliefsOption {

  final case class OtherReliefs(name: String, amount: AmountInPence) extends OtherReliefsOption

  case object NoOtherReliefs extends OtherReliefsOption

  implicit class OtherReliefsOptionOps(private val o: OtherReliefsOption) extends AnyVal {
    def fold[A](
      ifOtherReliefs: OtherReliefs => A,
      ifNoOtherReliefs: () => A
    ): A =
      o match {
        case NoOtherReliefs      => ifNoOtherReliefs()
        case value: OtherReliefs => ifOtherReliefs(value)
      }
  }

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val format: OFormat[OtherReliefsOption] = derived.oformat()

}
