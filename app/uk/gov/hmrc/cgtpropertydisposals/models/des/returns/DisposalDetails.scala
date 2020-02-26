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

import java.time.LocalDate

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import cats.syntax.order._

final case class DisposalDetails(
  disposalDate: LocalDate,
  addressDetails: AddresssDetails,
  assetType: String,
  acquisitionType: String,
  landRegistry: Boolean,
  acquisitionPrice: BigDecimal,
  rebased: Boolean,
  disposalPrice: BigDecimal,
  improvements: Boolean,
  percentOwned: Option[BigDecimal],
  acquiredDate: Option[LocalDate],
  rebasedAmount: Option[BigDecimal],
  disposalType: Option[String],
  improvementCosts: Option[BigDecimal],
  acquisitionFees: Option[BigDecimal],
  disposalFees: Option[BigDecimal],
  initialGain: Option[BigDecimal],
  initialLoss: Option[BigDecimal]
)

object DisposalDetails {

  def apply(c: CompleteReturn): DisposalDetails = {
    val addressDetails   = AddresssDetails(c)
    val calculatedTaxDue = c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue

    DisposalDetails(
      disposalDate     = c.triageAnswers.disposalDate.value,
      addressDetails   = addressDetails,
      assetType        = AssetType(c),
      acquisitionType  = AcquisitionMethod(c),
      landRegistry     = false,
      acquisitionPrice = c.acquisitionDetails.acquisitionPrice.inPounds,
      rebased          = c.acquisitionDetails.rebasedAcquisitionPrice.isDefined,
      disposalPrice    = c.disposalDetails.disposalPrice.inPounds,
      improvements     = c.acquisitionDetails.improvementCosts > AmountInPence.zero,
      percentOwned     = Some(c.disposalDetails.shareOfProperty.percentageValue),
      acquiredDate     = Some(c.acquisitionDetails.acquisitionDate.value),
      rebasedAmount    = c.acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds),
      disposalType     = DisposalMethod(c),
      improvementCosts = improvementCosts(c),
      acquisitionFees  = Some(c.acquisitionDetails.acquisitionFees.inPounds),
      disposalFees     = Some(c.disposalDetails.disposalFees.inPounds),
      initialGain      = getInitialGainOrLoss(calculatedTaxDue)._1,
      initialLoss      = getInitialGainOrLoss(calculatedTaxDue)._2
    )
  }

  private def getInitialGainOrLoss(calculatedTaxDue: CalculatedTaxDue): (Option[BigDecimal], Option[BigDecimal]) = {
    val value = calculatedTaxDue.initialGainOrLoss

    if (value < AmountInPence.zero)
      None -> Some(value.inPounds() * -1)
    else
      Some(value.inPounds()) -> None
  }

  private def improvementCosts(c: CompleteReturn): Option[BigDecimal] =
    if (c.acquisitionDetails.improvementCosts > AmountInPence.zero)
      Some(c.acquisitionDetails.improvementCosts.inPounds())
    else None

  implicit val disposalDetailsFormat: OFormat[DisposalDetails] = Json.format[DisposalDetails]

}
