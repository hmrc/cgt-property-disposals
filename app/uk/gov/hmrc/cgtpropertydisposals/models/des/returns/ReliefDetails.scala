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

import cats.syntax.order._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn

final case class ReliefDetails(
  reliefs: Boolean,
  privateResRelief: Option[BigDecimal],
  lettingsReflief: Option[BigDecimal], // this misspelling is intentional - DES are using the incorrect spelling
  giftHoldOverRelief: Option[BigDecimal],
  otherRelief: Option[String],
  otherReliefAmount: Option[BigDecimal]
)

object ReliefDetails {

  def apply(cr: CompleteReturn): ReliefDetails =
    ReliefDetails(
      reliefs            = reliefs(cr),
      privateResRelief   = privateResRelief(cr),
      lettingsReflief    = lettingsRelief(cr),
      giftHoldOverRelief = None,
      otherRelief        = cr.reliefDetails.otherReliefs.map(_.fold(r => r.name, () => "none")),
      otherReliefAmount  = cr.reliefDetails.otherReliefs.map(_.fold(r => r.amount.inPounds(), () => 0))
    )

  private def reliefs(cr: CompleteReturn): Boolean =
    cr.reliefDetails.privateResidentsRelief > AmountInPence.zero &
      cr.reliefDetails.lettingsRelief > AmountInPence.zero &
      cr.reliefDetails.otherReliefs.map(_.fold(_ => true, () => false)).isDefined

  private def privateResRelief(cr: CompleteReturn): Option[BigDecimal] =
    if (cr.reliefDetails.privateResidentsRelief > AmountInPence.zero)
      Some(cr.reliefDetails.privateResidentsRelief.inPounds())
    else None

  private def lettingsRelief(cr: CompleteReturn): Option[BigDecimal] =
    if (cr.reliefDetails.lettingsRelief > AmountInPence.zero)
      Some(cr.reliefDetails.lettingsRelief.inPounds())
    else None

  implicit val reliefDetailsFormat: OFormat[ReliefDetails] = Json.format[ReliefDetails]

}
