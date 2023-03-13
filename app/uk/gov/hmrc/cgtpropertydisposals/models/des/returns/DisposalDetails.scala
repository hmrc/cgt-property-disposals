/*
 * Copyright 2023 HM Revenue & Customs
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
import play.api.libs.json.{JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns._

import java.time.LocalDate

sealed trait DisposalDetails extends Product with Serializable

object DisposalDetails {

  final case class SingleDisposalDetails(
    disposalDate: LocalDate,
    addressDetails: AddressDetails,
    assetType: DesAssetTypeValue,
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
    assetType: DesAssetTypeValue,
    acquisitionType: DesAcquisitionType,
    landRegistry: Boolean,
    acquisitionPrice: BigDecimal,
    disposalPrice: BigDecimal,
    rebased: Boolean,
    improvements: Boolean,
    initialGain: Option[BigDecimal],
    initialLoss: Option[BigDecimal]
  ) extends DisposalDetails

  final case class SingleMixedUseDisposalDetails(
    disposalDate: LocalDate,
    addressDetails: AddressDetails,
    assetType: DesAssetTypeValue,
    disposalType: DesDisposalType,
    acquisitionType: DesAcquisitionType,
    landRegistry: Boolean,
    acquisitionPrice: BigDecimal,
    disposalPrice: BigDecimal,
    rebased: Boolean,
    improvements: Boolean,
    initialGain: Option[BigDecimal],
    initialLoss: Option[BigDecimal]
  ) extends DisposalDetails

  def apply(c: CompleteReturn): DisposalDetails = {
    def extractGainOrLoss(a: Option[AmountInPence]): (Option[BigDecimal], Option[BigDecimal]) =
      a.fold[(Option[BigDecimal], Option[BigDecimal])](None -> None) { f =>
        if (f < AmountInPence.zero)
          None               -> Some(-f.inPounds())
        else
          Some(f.inPounds()) -> None
      }

    c match {
      case s: CompleteSingleDisposalReturn =>
        val initialGainOrLoss: (Option[BigDecimal], Option[BigDecimal]) =
          extractGainOrLoss(s.initialGainOrLoss.orElse(s.gainOrLossAfterReliefs))

        SingleDisposalDetails(
          disposalDate = s.triageAnswers.disposalDate.value,
          addressDetails = Address.toAddressDetails(s.propertyAddress),
          assetType = DesAssetTypeValue(s),
          acquisitionType = DesAcquisitionType(s.acquisitionDetails.acquisitionMethod),
          landRegistry = false,
          acquisitionPrice = s.acquisitionDetails.acquisitionPrice.inPounds(),
          rebased = s.acquisitionDetails.shouldUseRebase,
          rebasedAmount = s.acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds()),
          disposalPrice = s.disposalDetails.disposalPrice.inPounds(),
          improvements = s.acquisitionDetails.improvementCosts > AmountInPence.zero,
          improvementCosts = improvementCosts(s),
          percentOwned = s.disposalDetails.shareOfProperty.percentageValue,
          acquiredDate = s.acquisitionDetails.acquisitionDate.value,
          disposalType = DesDisposalType(s.triageAnswers.disposalMethod),
          acquisitionFees = s.acquisitionDetails.acquisitionFees.inPounds(),
          disposalFees = s.disposalDetails.disposalFees.inPounds(),
          initialGain = initialGainOrLoss._1,
          initialLoss = initialGainOrLoss._2
        )

      case m: CompleteMultipleDisposalsReturn =>
        val gainOrLossAfterReliefs: (Option[BigDecimal], Option[BigDecimal]) = extractGainOrLoss(
          m.gainOrLossAfterReliefs
        )

        MultipleDisposalDetails(
          disposalDate = m.examplePropertyDetailsAnswers.disposalDate.value,
          addressDetails = Address.toAddressDetails(m.examplePropertyDetailsAnswers.address),
          assetType = DesAssetTypeValue(m),
          acquisitionType = DesAcquisitionType.Other("not captured for multiple disposals"),
          landRegistry = false,
          acquisitionPrice = m.examplePropertyDetailsAnswers.acquisitionPrice.inPounds(),
          disposalPrice = m.examplePropertyDetailsAnswers.disposalPrice.inPounds(),
          rebased = false,
          improvements = false,
          initialGain = gainOrLossAfterReliefs._1,
          initialLoss = gainOrLossAfterReliefs._2
        )

      case s: CompleteSingleIndirectDisposalReturn =>
        val gainOrLossAfterReliefs: (Option[BigDecimal], Option[BigDecimal]) = extractGainOrLoss(
          s.gainOrLossAfterReliefs
        )

        SingleDisposalDetails(
          disposalDate = s.triageAnswers.disposalDate.value,
          addressDetails = Address.toAddressDetails(s.companyAddress),
          assetType = DesAssetTypeValue(s),
          acquisitionType = DesAcquisitionType(s.acquisitionDetails.acquisitionMethod),
          landRegistry = false,
          acquisitionPrice = s.acquisitionDetails.acquisitionPrice.inPounds(),
          rebased = s.acquisitionDetails.shouldUseRebase,
          rebasedAmount = s.acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds()),
          disposalPrice = s.disposalDetails.disposalPrice.inPounds(),
          improvements = false,
          improvementCosts = None,
          percentOwned = s.disposalDetails.shareOfProperty.percentageValue,
          acquiredDate = s.acquisitionDetails.acquisitionDate.value,
          disposalType = DesDisposalType(s.triageAnswers.disposalMethod),
          acquisitionFees = s.acquisitionDetails.acquisitionFees.inPounds(),
          disposalFees = s.disposalDetails.disposalFees.inPounds(),
          initialGain = gainOrLossAfterReliefs._1,
          initialLoss = gainOrLossAfterReliefs._2
        )

      case m: CompleteMultipleIndirectDisposalReturn =>
        val gainOrLossAfterReliefs: (Option[BigDecimal], Option[BigDecimal]) = extractGainOrLoss(
          m.gainOrLossAfterReliefs
        )

        MultipleDisposalDetails(
          disposalDate = m.triageAnswers.completionDate.value,
          addressDetails = Address.toAddressDetails(m.exampleCompanyDetailsAnswers.address),
          assetType = DesAssetTypeValue(m),
          acquisitionType = DesAcquisitionType.Other("not captured for multiple disposals"),
          landRegistry = false,
          acquisitionPrice = m.exampleCompanyDetailsAnswers.acquisitionPrice.inPounds(),
          disposalPrice = m.exampleCompanyDetailsAnswers.disposalPrice.inPounds(),
          rebased = false,
          improvements = false,
          initialGain = gainOrLossAfterReliefs._1,
          initialLoss = gainOrLossAfterReliefs._2
        )

      case s: CompleteSingleMixedUseDisposalReturn =>
        val gainOrLossAfterReliefs: (Option[BigDecimal], Option[BigDecimal]) = extractGainOrLoss(
          s.gainOrLossAfterReliefs
        )

        SingleMixedUseDisposalDetails(
          disposalDate = s.triageAnswers.disposalDate.value,
          addressDetails = Address.toAddressDetails(s.propertyDetailsAnswers.address),
          assetType = DesAssetTypeValue(s),
          disposalType = DesDisposalType(s.triageAnswers.disposalMethod),
          acquisitionType = DesAcquisitionType.Other("not captured for single mixed use"),
          landRegistry = false,
          acquisitionPrice = s.propertyDetailsAnswers.acquisitionPrice.inPounds(),
          disposalPrice = s.propertyDetailsAnswers.disposalPrice.inPounds(),
          rebased = false,
          improvements = false,
          initialGain = gainOrLossAfterReliefs._1,
          initialLoss = gainOrLossAfterReliefs._2
        )
    }
  }

  private def improvementCosts(c: CompleteSingleDisposalReturn): Option[BigDecimal] =
    if (c.acquisitionDetails.improvementCosts > AmountInPence.zero)
      Some(c.acquisitionDetails.improvementCosts.inPounds())
    else None

  implicit val singleDisposalDetailsFormat: OFormat[SingleDisposalDetails]                 = Json.format
  implicit val multipleDisposalsDetailsFormat: OFormat[MultipleDisposalDetails]            = Json.format
  implicit val singleMixedUseDisposalDetailsFormat: OFormat[SingleMixedUseDisposalDetails] = Json.format

  implicit val disposalDetailsFormat: OFormat[DisposalDetails] = OFormat(
    { json: JsValue =>
      singleDisposalDetailsFormat
        .reads(json)
        .orElse(singleMixedUseDisposalDetailsFormat.reads(json))
        .orElse(multipleDisposalsDetailsFormat.reads(json))
    },
    { d: DisposalDetails =>
      d match {
        case s: SingleDisposalDetails         => singleDisposalDetailsFormat.writes(s)
        case m: MultipleDisposalDetails       => multipleDisposalsDetailsFormat.writes(m)
        case s: SingleMixedUseDisposalDetails => singleMixedUseDisposalDetailsFormat.writes(s)
      }
    }
  )

}
