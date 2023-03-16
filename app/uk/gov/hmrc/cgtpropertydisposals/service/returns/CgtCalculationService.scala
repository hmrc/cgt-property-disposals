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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.syntax.order._
import com.google.inject.{ImplementedBy, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmountInPenceWithSource, AssetType, CalculatedTaxDue, FurtherReturnCalculationData, Source, TaxableAmountOfMoney, TaxableGainOrLossCalculation, TaxableGainOrLossCalculationRequest, YearToDateLiabilityCalculation, YearToDateLiabilityCalculationRequest}

@ImplementedBy(classOf[CgtCalculationServiceImpl])
trait CgtCalculationService {

  def calculateTaxDue(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    address: UkAddress,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    reliefDetails: CompleteReliefDetailsAnswers,
    exemptionAndLosses: CompleteExemptionAndLossesAnswers,
    estimatedIncome: AmountInPence,
    personalAllowance: AmountInPence,
    initialGainOrLoss: Option[AmountInPence],
    isATrust: Boolean
  ): CalculatedTaxDue

  def calculateTaxableGainOrLoss(request: TaxableGainOrLossCalculationRequest): TaxableGainOrLossCalculation
  def calculateYearToDateLiability(request: YearToDateLiabilityCalculationRequest): YearToDateLiabilityCalculation

}

@Singleton
class CgtCalculationServiceImpl extends CgtCalculationService {

  def calculateTaxDue(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    address: UkAddress,
    disposalDetails: CompleteDisposalDetailsAnswers,
    acquisitionDetails: CompleteAcquisitionDetailsAnswers,
    reliefDetails: CompleteReliefDetailsAnswers,
    exemptionAndLosses: CompleteExemptionAndLossesAnswers,
    estimatedIncome: AmountInPence,
    personalAllowance: AmountInPence,
    initialGainOrLoss: Option[AmountInPence],
    isATrust: Boolean
  ): CalculatedTaxDue = {
    val disposalAmountLessCosts: AmountInPence =
      disposalDetails.disposalPrice -- disposalDetails.disposalFees

    val acquisitionPrice: AmountInPence = acquisitionDetails.rebasedAcquisitionPrice match {
      case Some(r) if acquisitionDetails.shouldUseRebase => r
      case _                                             => acquisitionDetails.acquisitionPrice
    }

    val acquisitionAmountPlusCosts: AmountInPence =
      acquisitionPrice ++
        acquisitionDetails.improvementCosts ++
        acquisitionDetails.acquisitionFees

    val initialGainOrLossWithSource: AmountInPenceWithSource = initialGainOrLoss match {
      case Some(a: AmountInPence) =>
        AmountInPenceWithSource(
          amount = a,
          source = Source.UserSupplied
        )
      case _                      =>
        AmountInPenceWithSource(
          amount = disposalAmountLessCosts -- acquisitionAmountPlusCosts,
          source = Source.Calculated
        )
    }

    val totalReliefs: AmountInPence = {
      reliefDetails.privateResidentsRelief ++ reliefDetails.lettingsRelief
    }

    val gainOrLossAfterReliefs: AmountInPence =
      if (initialGainOrLossWithSource.amount > AmountInPence.zero)
        (initialGainOrLossWithSource.amount -- totalReliefs).withFloorZero
      else if (initialGainOrLossWithSource.amount < AmountInPence.zero)
        (initialGainOrLossWithSource.amount ++ totalReliefs).withCeilingZero
      else
        AmountInPence.zero

    val TaxableGainOrLossCalculation(taxableGain, _, gainOrLossAfterInYearLosses, yearPosition, _, _) =
      calculateTaxableGainOrLoss(
        gainOrLossAfterReliefs,
        exemptionAndLosses,
        List.empty,
        address
      )

    val YearToDateLiabilityCalculation(_, _, _, taxableIncome, lowerBandTax, higherBandTax, taxDue) =
      calculateYearToDateLiability(
        triageAnswers,
        taxableGain,
        estimatedIncome,
        personalAllowance,
        isATrust
      )

    if (taxableGain <= AmountInPence.zero)
      NonGainCalculatedTaxDue(
        disposalAmountLessCosts,
        acquisitionAmountPlusCosts,
        initialGainOrLossWithSource,
        gainOrLossAfterInYearLosses,
        totalReliefs,
        gainOrLossAfterReliefs,
        yearPosition,
        taxableGain,
        AmountInPence.zero
      )
    else {
      GainCalculatedTaxDue(
        disposalAmountLessCosts,
        acquisitionAmountPlusCosts,
        initialGainOrLossWithSource,
        gainOrLossAfterInYearLosses,
        totalReliefs,
        gainOrLossAfterReliefs,
        yearPosition,
        taxableGain,
        taxableIncome,
        lowerBandTax,
        higherBandTax,
        taxDue
      )
    }
  }

  def calculateTaxableGainOrLoss(request: TaxableGainOrLossCalculationRequest): TaxableGainOrLossCalculation =
    calculateTaxableGainOrLoss(
      request.gainOrLossAfterReliefs,
      request.exemptionAndLossesAnswers,
      request.calculationData,
      request.address
    )

  private def calculateTaxableGainOrLoss(
    gainOrLossAfterReliefs: AmountInPence,
    exemptionAndLosses: CompleteExemptionAndLossesAnswers,
    calculationData: List[FurtherReturnCalculationData],
    address: Address.UkAddress
  ): TaxableGainOrLossCalculation = {
    val calculationDataWithCurrentData =
      FurtherReturnCalculationData(address, gainOrLossAfterReliefs) :: calculationData
    val gainsAfterReliefs              =
      calculationDataWithCurrentData.map(r =>
        r.copy(gainOrLossAfterReliefs =
          if (r.gainOrLossAfterReliefs.isPositive | calculationData.isEmpty) r.gainOrLossAfterReliefs
          else AmountInPence.zero
        )
      )

    val totalGainsAfterReliefs = gainsAfterReliefs
      .map(_.gainOrLossAfterReliefs)
      .foldLeft(AmountInPence.zero)(_ ++ _)

    val gainOrLossAfterInYearLosses =
      totalGainsAfterReliefs -- exemptionAndLosses.inYearLosses

    val previousYearsLosses =
      if (gainOrLossAfterInYearLosses < AmountInPence.zero)
        AmountInPence.zero
      else
        exemptionAndLosses.previousYearsLosses

    val yearPosition: AmountInPence =
      if (gainOrLossAfterInYearLosses > AmountInPence.zero)
        (gainOrLossAfterInYearLosses -- exemptionAndLosses.annualExemptAmount).withFloorZero
      else
        gainOrLossAfterInYearLosses

    val previousYearLossesUsed =
      if (yearPosition > AmountInPence.zero) previousYearsLosses
      else AmountInPence.zero

    val taxableGainOrLoss =
      if (yearPosition > AmountInPence.zero)
        (yearPosition -- previousYearLossesUsed).withFloorZero
      else
        yearPosition

    TaxableGainOrLossCalculation(
      taxableGainOrLoss,
      previousYearLossesUsed,
      gainOrLossAfterInYearLosses,
      yearPosition,
      gainsAfterReliefs,
      totalGainsAfterReliefs
    )
  }

  def calculateYearToDateLiability(request: YearToDateLiabilityCalculationRequest): YearToDateLiabilityCalculation =
    calculateYearToDateLiability(
      request.triageAnswers,
      request.taxableGain,
      request.estimatedIncome,
      request.personalAllowance,
      request.isATrust
    )

  private def calculateYearToDateLiability(
    triageAnswers: CompleteSingleDisposalTriageAnswers,
    taxableGain: AmountInPence,
    estimatedIncome: AmountInPence,
    personalAllowance: AmountInPence,
    isATrust: Boolean
  ): YearToDateLiabilityCalculation = {
    val taxYear                        = triageAnswers.disposalDate.taxYear
    val (lowerBandRate, higherTaxRate) =
      triageAnswers.assetType match {
        case AssetType.Residential => taxYear.cgtRateLowerBandResidential    -> taxYear.cgtRateHigherBandResidential
        case _                     => taxYear.cgtRateLowerBandNonResidential -> taxYear.cgtRateHigherBandNonResidential
      }
    val taxableIncome                  = (estimatedIncome -- personalAllowance).withFloorZero
    val lowerBandTax                   =
      if (isATrust)
        TaxableAmountOfMoney(lowerBandRate, AmountInPence(0L))
      else
        TaxableAmountOfMoney(
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

    YearToDateLiabilityCalculation(
      taxableGain,
      estimatedIncome,
      personalAllowance,
      taxableIncome,
      lowerBandTax,
      higherBandTax,
      taxDue
    )
  }

}
