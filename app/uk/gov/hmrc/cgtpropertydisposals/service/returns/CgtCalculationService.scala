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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.syntax.order._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmountInPenceWithSource, AssetType, CalculatedTaxDue, Source, TaxableAmountOfMoney}

@ImplementedBy(classOf[CgtCalculationServiceImpl])
trait CgtCalculationService {

  def calculateTaxDue(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    reliefDetails: CompleteReliefDetailsAnswers,
    exemptionAndLosses: CompleteExemptionAndLossesAnswers,
    estimatedIncome: AmountInPence,
    personalAllowance: AmountInPence,
    initialGainOrLossDetails: Option[AmountInPence]
  ): CalculatedTaxDue

}

@Singleton
class CgtCalculationServiceImpl extends CgtCalculationService {

  def calculateTaxDue(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    reliefDetails: CompleteReliefDetailsAnswers,
    exemptionAndLosses: CompleteExemptionAndLossesAnswers,
    estimatedIncome: AmountInPence,
    personalAllowance: AmountInPence,
    initialGainOrLoss: Option[AmountInPence]
  ): CalculatedTaxDue = {
    val disposalAmountLessCosts: AmountInPence =
      disposalDetails.disposalPrice -- disposalDetails.disposalFees

    val acquisitionAmountPlusCosts: AmountInPence =
      acquisitionDetails.rebasedAcquisitionPrice.getOrElse(acquisitionDetails.acquisitionPrice) ++
        acquisitionDetails.improvementCosts ++
        acquisitionDetails.acquisitionFees

    val initialGainOrLossWithSource: AmountInPenceWithSource = initialGainOrLoss match {
      case Some(a: AmountInPence) =>
        AmountInPenceWithSource(
          amount = a,
          source = Source.UserSupplied
        )
      case _ =>
        AmountInPenceWithSource(
          amount = disposalAmountLessCosts -- acquisitionAmountPlusCosts,
          source = Source.Calculated
        )
    }

    val totalReliefs: AmountInPence = {
      val otherReliefs =
        reliefDetails.otherReliefs.map(_.fold(_.amount, () => AmountInPence.zero)).getOrElse(AmountInPence.zero)
      reliefDetails.privateResidentsRelief ++ reliefDetails.lettingsRelief ++ otherReliefs
    }

    val gainOrLossAfterReliefs: AmountInPence =
      if (initialGainOrLossWithSource.amount > AmountInPence.zero)
        (initialGainOrLossWithSource.amount -- totalReliefs).withFloorZero
      else if (initialGainOrLossWithSource.amount < AmountInPence.zero)
        (initialGainOrLossWithSource.amount ++ totalReliefs).withCeilingZero
      else
        AmountInPence.zero

    val totalLosses: AmountInPence = {
      val previousYearsLosses =
        if (gainOrLossAfterReliefs < AmountInPence.zero ||
            (gainOrLossAfterReliefs -- exemptionAndLosses.inYearLosses) < AmountInPence.zero)
          AmountInPence.zero
        else
          exemptionAndLosses.previousYearsLosses

      exemptionAndLosses.inYearLosses ++ previousYearsLosses
    }

    val gainOrLossAfterLosses: AmountInPence =
      if (gainOrLossAfterReliefs >= AmountInPence.zero)
        (gainOrLossAfterReliefs -- totalLosses).withFloorZero
      else
        (gainOrLossAfterReliefs ++ totalLosses).withCeilingZero

    val taxableGain: AmountInPence =
      if (gainOrLossAfterLosses > AmountInPence.zero)
        (gainOrLossAfterLosses -- exemptionAndLosses.annualExemptAmount).withFloorZero
      else
        gainOrLossAfterLosses

    if (taxableGain <= AmountInPence.zero)
      NonGainCalculatedTaxDue(
        disposalAmountLessCosts,
        acquisitionAmountPlusCosts,
        initialGainOrLossWithSource,
        totalReliefs,
        gainOrLossAfterReliefs,
        totalLosses,
        gainOrLossAfterLosses,
        taxableGain,
        AmountInPence.zero
      )
    else {
      val taxYear       = triageAnswers.disposalDate.taxYear
      val taxableIncome = (estimatedIncome -- personalAllowance).withFloorZero
      val (lowerBandRate, higherTaxRate) =
        triageAnswers.assetType match {
          case AssetType.Residential => taxYear.cgtRateLowerBandResidential    -> taxYear.cgtRateHigherBandResidential
          case _                     => taxYear.cgtRateLowerBandNonResidential -> taxYear.cgtRateHigherBandNonResidential
        }
      val lowerBandTax = TaxableAmountOfMoney(
        lowerBandRate,
        AmountInPence.zero.max(
          taxableGain.min(
            (taxYear.incomeTaxHigherRateThreshold -- taxableIncome).withFloorZero
          )
        )
      )
      val higherBandTax = TaxableAmountOfMoney(
        higherTaxRate,
        (taxableGain -- lowerBandTax.taxableAmount).withFloorZero
      )

      val taxDue = lowerBandTax.taxDue() ++ higherBandTax.taxDue()

      GainCalculatedTaxDue(
        disposalAmountLessCosts,
        acquisitionAmountPlusCosts,
        initialGainOrLossWithSource,
        totalReliefs,
        gainOrLossAfterReliefs,
        totalLosses,
        gainOrLossAfterLosses,
        taxableGain,
        taxableIncome,
        lowerBandTax,
        higherBandTax,
        taxDue
      )
    }
  }

}
