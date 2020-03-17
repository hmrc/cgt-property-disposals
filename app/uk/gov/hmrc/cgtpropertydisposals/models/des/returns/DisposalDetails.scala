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

import cats.syntax.order._
import play.api.libs.json.{JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns._

sealed trait DisposalDetails extends Product with Serializable

object DisposalDetails {

  final case class SingleDisposalDetails(
    disposalDate: LocalDate,
    addressDetails: AddressDetails,
    assetType: DesAssetType,
    acquisitionType: DesAcquisitionType,
    landRegistry: Boolean,
    acquisitionPrice: BigDecimal,
    rebased: Boolean,
    rebasedAmount: Option[BigDecimal],
    disposalPrice: BigDecimal,
    improvements: Boolean,
    improvementCosts: Option[BigDecimal],
    percentOwned: BigDecimal,
    acquiredDate: LocalDate,
    disposalType: DesDisposalType,
    acquisitionFees: BigDecimal,
    disposalFees: BigDecimal,
    initialGain: Option[BigDecimal],
    initialLoss: Option[BigDecimal]
  ) extends DisposalDetails

  final case class MultipleDisposalDetails(
    disposalDate: LocalDate,
    addressDetails: AddressDetails,
    assetType: DesAssetType,
    acquisitionType: DesAcquisitionType,
    landRegistry: Boolean,
    acquisitionPrice: BigDecimal,
    disposalPrice: BigDecimal,
    initialGain: BigDecimal,
    initialLoss: BigDecimal,
    rebased: Boolean = false
  ) extends DisposalDetails

  def apply(c: CompleteReturn): DisposalDetails = {
    val addressDetails   = Address.toAddressDetails(c.propertyAddress)
    val calculatedTaxDue = c.yearToDateLiabilityAnswers.calculatedTaxDue

    SingleDisposalDetails(
      disposalDate     = c.triageAnswers.disposalDate.value,
      addressDetails   = addressDetails,
      assetType        = DesAssetType(c),
      acquisitionType  = DesAcquisitionType(c),
      landRegistry     = false,
      acquisitionPrice = c.acquisitionDetails.acquisitionPrice.inPounds(),
      rebased          = c.acquisitionDetails.rebasedAcquisitionPrice.isDefined,
      rebasedAmount    = c.acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds()),
      disposalPrice    = c.disposalDetails.disposalPrice.inPounds(),
      improvements     = c.acquisitionDetails.improvementCosts > AmountInPence.zero,
      improvementCosts = improvementCosts(c),
      percentOwned     = c.disposalDetails.shareOfProperty.percentageValue,
      acquiredDate     = c.acquisitionDetails.acquisitionDate.value,
      disposalType     = DesDisposalType(c),
      acquisitionFees  = c.acquisitionDetails.acquisitionFees.inPounds(),
      disposalFees     = c.disposalDetails.disposalFees.inPounds(),
      initialGain      = getInitialGainOrLoss(calculatedTaxDue)._1,
      initialLoss      = getInitialGainOrLoss(calculatedTaxDue)._2
    )
  }

  private def getInitialGainOrLoss(calculatedTaxDue: CalculatedTaxDue): (Option[BigDecimal], Option[BigDecimal]) = {
    val value = calculatedTaxDue.initialGainOrLoss.amount

    if (value < AmountInPence.zero)
      None -> Some(value.inPounds() * -1)
    else
      Some(value.inPounds()) -> None
  }

  private def improvementCosts(c: CompleteReturn): Option[BigDecimal] =
    if (c.acquisitionDetails.improvementCosts > AmountInPence.zero)
      Some(c.acquisitionDetails.improvementCosts.inPounds())
    else None

  implicit val singleDisposalDetailsFormat: OFormat[SingleDisposalDetails]      = Json.format
  implicit val multipleDisposalsDetailsFormat: OFormat[MultipleDisposalDetails] = Json.format
  implicit val disposalDetailsFormat: OFormat[DisposalDetails] = OFormat(
    { json: JsValue =>
      singleDisposalDetailsFormat.reads(json).orElse(multipleDisposalsDetailsFormat.reads(json))
    }, { d: DisposalDetails =>
      d match {
        case s: SingleDisposalDetails   => singleDisposalDetailsFormat.writes(s)
        case m: MultipleDisposalDetails => multipleDisposalsDetailsFormat.writes(m)
      }
    }
  )

}
