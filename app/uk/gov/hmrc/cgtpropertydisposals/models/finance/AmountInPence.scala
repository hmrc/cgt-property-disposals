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

package uk.gov.hmrc.cgtpropertydisposals.models.finance

import cats.Order
import cats.instances.long._
import play.api.libs.functional.syntax._
import play.api.libs.json.Format

final case class AmountInPence(value: Long)

object AmountInPence {

  val zero: AmountInPence = AmountInPence(0L)

  implicit class AmountInPenceOps(private val a: AmountInPence) extends AnyVal {
    def inPounds(): BigDecimal = a.value / BigDecimal("100")

    def ++(other: AmountInPence): AmountInPence = AmountInPence(a.value + other.value)

    def --(other: AmountInPence): AmountInPence = AmountInPence(a.value - other.value)

    def withFloorZero: AmountInPence = if a.value < 0L then AmountInPence.zero else a

    def withCeilingZero: AmountInPence = if a.value > 0L then AmountInPence.zero else a

    def isPositive: Boolean = a.value > 0L

  }

  implicit val order: Order[AmountInPence] = Order.by[AmountInPence, Long](_.value)

  implicit val format: Format[AmountInPence] =
    implicitly[Format[Long]].inmap(AmountInPence(_), _.value)

  def fromPounds(amount: BigDecimal): AmountInPence = AmountInPence((amount * BigDecimal("100")).toLong)

}
