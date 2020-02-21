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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn
import cats.syntax.order._

final case class ReliefDetails(
  reliefs: Boolean,
  privateResRelief: Option[BigDecimal],
  lettingsReflief: Option[BigDecimal],
  giftHoldOverRelief: Option[BigDecimal],
  otherRelief: Option[String],
  otherReliefAmount: Option[BigDecimal]
)

object ReliefDetails {

  def apply(cr: CompleteReturn): ReliefDetails =
    ReliefDetails(
      reliefs            = reliefs(cr),
      privateResRelief   = Some(cr.reliefDetails.privateResidentsRelief.inPounds),
      lettingsReflief    = Some(cr.reliefDetails.lettingsRelief.inPounds),
      giftHoldOverRelief = None,
      otherRelief        = cr.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.name), () => None)),
      otherReliefAmount  = cr.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.amount.inPounds), () => None))
    )

  def reliefs(cr: CompleteReturn): Boolean =
    cr.reliefDetails.privateResidentsRelief > AmountInPence.zero &
      cr.reliefDetails.lettingsRelief > AmountInPence.zero &
      cr.reliefDetails.otherReliefs.map(_.fold(_ => true, () => false)).isDefined

  implicit val reliefDetailsFormat: OFormat[ReliefDetails] = Json.format[ReliefDetails]

}
