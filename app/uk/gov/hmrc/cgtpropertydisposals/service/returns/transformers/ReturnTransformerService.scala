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

package uk.gov.hmrc.cgtpropertydisposals.service.returns.transformers

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.bigDecimal._
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.CustomerType.Trust
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails, SingleMixedUseDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AssetType.{IndirectDisposal, MixedUse}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExampleCompanyDetailsAnswers.CompleteExampleCompanyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MixedUsePropertyDetailsAnswers.CompleteMixedUsePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, Validation, invalid}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{CgtCalculationService, TaxYearService}

import java.time.LocalDate

@ImplementedBy(classOf[ReturnTransformerServiceImpl])
trait ReturnTransformerService {

  def toCompleteReturn(desReturn: DesReturnDetails): Either[Error, DisplayReturn]

}

@Singleton
class ReturnTransformerServiceImpl @Inject() (
  cgtCalculationService: CgtCalculationService,
  taxYearService: TaxYearService
) extends ReturnTransformerService {

  override def toCompleteReturn(desReturn: DesReturnDetails): Either[Error, DisplayReturn] = {
    lazy val returnType = desReturn.returnType match {
      case _: CreateReturnType =>
        if isFurtherOrAmendReturn(desReturn) then ReturnType.FurtherReturn else ReturnType.FirstReturn
      case _: AmendReturnType  => ReturnType.AmendedReturn
    }

    fromDesReturn(desReturn).toEither
      .map(completeReturn => DisplayReturn(completeReturn, returnType))
      .leftMap(e => Error(s"Could not convert des response to complete return: [${e.toList.mkString("; ")}]"))
  }

  private def fromDesReturn(
    desReturn: DesReturnDetails
  ): ValidatedNel[String, CompleteReturn] =
    desReturn.disposalDetails match {
      case (singleDisposalDetails: SingleDisposalDetails) :: Nil =>
        if singleDisposalDetails.assetType.isIndirectDisposal() then
          validateSingleIndirectDisposal(desReturn, singleDisposalDetails)
        else validateSingleDisposal(desReturn, singleDisposalDetails)

      case (multipleDisposalsDetails: MultipleDisposalDetails) :: Nil =>
        if multipleDisposalsDetails.assetType.isIndirectDisposal() then
          validateMultipleIndirectDisposal(desReturn, multipleDisposalsDetails)
        else validateMultipleDisposal(desReturn, multipleDisposalsDetails)

      case (singleMixedUseDisposalDetails: SingleMixedUseDisposalDetails) :: Nil =>
        validateSingleMixedUseMultipleDisposal(desReturn, singleMixedUseDisposalDetails)

      case other =>
        Invalid(
          NonEmptyList.one(
            s"Expected either one single disposal detail or one multiple disposal details but got ${other.length} disposals"
          )
        )
    }

  private def validateMultipleDisposal(
    desReturn: DesReturnDetails,
    multipleDisposalDetails: MultipleDisposalDetails
  ): ValidatedNel[String, CompleteMultipleDisposalsReturn] =
    (
      ukAddressValidation(multipleDisposalDetails.addressDetails),
      disposalDateValidation(multipleDisposalDetails.disposalDate),
      assetTypesValidation(multipleDisposalDetails),
      countryValidation(desReturn)
    ).mapN {
      case (
            address,
            disposalDate,
            assetTypes,
            country
          ) =>
        val triageAnswers = CompleteMultipleDisposalsTriageAnswers(
          getIndividualUserType(desReturn),
          desReturn.returnDetails.numberDisposals,
          country,
          assetTypes,
          Some(TaxYearExchanged(disposalDate.taxYear.startDateInclusive.getYear)),
          disposalDate.taxYear,
          None,
          CompletionDate(desReturn.returnDetails.completionDate)
        )

        val examplePropertyDetailsAnswers = CompleteExamplePropertyDetailsAnswers(
          address,
          disposalDate,
          AmountInPence.fromPounds(multipleDisposalDetails.disposalPrice),
          AmountInPence.fromPounds(multipleDisposalDetails.acquisitionPrice)
        )

        val exemptionAndLossesAnswers  = constructExemptionAndLossesAnswers(desReturn)
        val yearToDateLiabilityAnswers = constructNonCalculatedYearToDateAnswers(desReturn)
        val gainOrLossAfterReliefs     = constructInitialGainAnswers(multipleDisposalDetails)

        CompleteMultipleDisposalsReturn(
          triageAnswers,
          examplePropertyDetailsAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
          None,
          gainOrLossAfterReliefs,
          desReturn.returnDetails.attachmentUpload
        )
    }

  private def validateSingleDisposal(
    desReturn: DesReturnDetails,
    singleDisposalDetails: SingleDisposalDetails
  ): ValidatedNel[String, CompleteSingleDisposalReturn] =
    (
      ukAddressValidation(singleDisposalDetails.addressDetails),
      disposalDateValidation(singleDisposalDetails.disposalDate),
      assetTypeValidation(singleDisposalDetails),
      countryValidation(desReturn),
      reliefsValidation(desReturn)
    ).mapN {
      case (
            address,
            disposalDate,
            assetType,
            country,
            (reliefDetails, otherReliefsOption)
          ) =>
        val triageAnswers             =
          constructTriageAnswers(
            desReturn,
            country,
            disposalDate,
            DisposalMethod(singleDisposalDetails.disposalType),
            assetType
          )
        val disposalDetailsAnswers    = constructDisposalDetailsAnswers(singleDisposalDetails)
        val acquisitionDetailsAnswers = constructAcquisitionDetailsAnswers(singleDisposalDetails)
        val reliefAnswers             = constructReliefAnswers(reliefDetails, otherReliefsOption)
        val exemptionAndLossesAnswers = constructExemptionAndLossesAnswers(desReturn)
        val initialGainOrLoss         = constructInitialGainAnswers(singleDisposalDetails)

        val yearToDateLiabilityAnswers =
          if isFurtherOrAmendReturn(desReturn) || desReturn.incomeAllowanceDetails.estimatedIncome.isEmpty then
            Left(constructNonCalculatedYearToDateAnswers(desReturn))
          else {
            val estimatedIncome =
              zeroOrAmountInPenceFromPounds(desReturn.incomeAllowanceDetails.estimatedIncome)

            val personalAllowance                  =
              desReturn.incomeAllowanceDetails.personalAllowance.map(AmountInPence.fromPounds)
            val calculatedTaxDue: CalculatedTaxDue = cgtCalculationService.calculateTaxDue(
              triageAnswers,
              address,
              disposalDetailsAnswers,
              acquisitionDetailsAnswers,
              reliefAnswers,
              exemptionAndLossesAnswers,
              estimatedIncome,
              personalAllowance.getOrElse(AmountInPence.zero),
              initialGainOrLoss,
              isATrust = desReturn.returnDetails.customerType match {
                case Trust => true
                case _     => false
              }
            )

            Right(
              CompleteCalculatedYTDAnswers(
                estimatedIncome,
                personalAllowance,
                desReturn.returnDetails.estimate,
                calculatedTaxDue,
                AmountInPence.fromPounds(desReturn.returnDetails.totalLiability),
                None
              )
            )
          }

        CompleteSingleDisposalReturn(
          triageAnswers,
          address,
          disposalDetailsAnswers,
          acquisitionDetailsAnswers,
          reliefAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers,
          if isFurtherOrAmendReturn(desReturn) then None else initialGainOrLoss,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
          None,
          if isFurtherOrAmendReturn(desReturn) then initialGainOrLoss else None,
          hasAttachments = desReturn.returnDetails.attachmentUpload
        )
    }

  private def validateSingleIndirectDisposal(
    desReturn: DesReturnDetails,
    singleDisposalDetails: SingleDisposalDetails
  ): ValidatedNel[String, CompleteSingleIndirectDisposalReturn] =
    (
      addressValidation(singleDisposalDetails.addressDetails),
      disposalDateValidation(singleDisposalDetails.disposalDate),
      countryValidation(desReturn)
    ).mapN {
      case (
            address,
            disposalDate,
            country
          ) =>
        val triageAnswers             =
          constructTriageAnswers(
            desReturn,
            country,
            disposalDate,
            DisposalMethod(singleDisposalDetails.disposalType),
            IndirectDisposal
          )
        val disposalDetailsAnswers    = constructDisposalDetailsAnswers(singleDisposalDetails)
        val acquisitionDetailsAnswers = constructAcquisitionDetailsAnswers(singleDisposalDetails)
        val exemptionAndLossesAnswers = constructExemptionAndLossesAnswers(desReturn)

        val yearToDateLiabilityAnswers =
          constructNonCalculatedYearToDateAnswers(desReturn)
        val gainOrLossAfterReliefs     = constructInitialGainAnswers(singleDisposalDetails)

        CompleteSingleIndirectDisposalReturn(
          triageAnswers,
          address,
          disposalDetailsAnswers,
          acquisitionDetailsAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
          None,
          gainOrLossAfterReliefs,
          hasAttachments = desReturn.returnDetails.attachmentUpload
        )
    }

  private def validateMultipleIndirectDisposal(
    desReturn: DesReturnDetails,
    multipleDisposalDetails: MultipleDisposalDetails
  ): ValidatedNel[String, CompleteMultipleIndirectDisposalReturn] =
    (
      addressValidation(multipleDisposalDetails.addressDetails),
      disposalDateValidation(multipleDisposalDetails.disposalDate),
      assetTypesValidation(multipleDisposalDetails),
      countryValidation(desReturn)
    ).mapN {
      case (
            address,
            disposalDate,
            assetTypes,
            country
          ) =>
        val triageAnswers = CompleteMultipleDisposalsTriageAnswers(
          getIndividualUserType(desReturn),
          desReturn.returnDetails.numberDisposals,
          country,
          assetTypes,
          Some(TaxYearExchanged(disposalDate.taxYear.startDateInclusive.getYear)),
          disposalDate.taxYear,
          Some(false),
          CompletionDate(desReturn.returnDetails.completionDate)
        )

        val exampleCompanyDetailsAnswers = CompleteExampleCompanyDetailsAnswers(
          address,
          AmountInPence.fromPounds(multipleDisposalDetails.disposalPrice),
          AmountInPence.fromPounds(multipleDisposalDetails.acquisitionPrice)
        )

        val exemptionAndLossesAnswers  = constructExemptionAndLossesAnswers(desReturn)
        val yearToDateLiabilityAnswers = constructNonCalculatedYearToDateAnswers(desReturn)
        val gainOrLossAfterReliefs     = constructInitialGainAnswers(multipleDisposalDetails)

        CompleteMultipleIndirectDisposalReturn(
          triageAnswers,
          exampleCompanyDetailsAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
          None,
          gainOrLossAfterReliefs,
          hasAttachments = desReturn.returnDetails.attachmentUpload
        )
    }

  private def validateSingleMixedUseMultipleDisposal(
    desReturn: DesReturnDetails,
    disposalDetails: SingleMixedUseDisposalDetails
  ): ValidatedNel[String, CompleteSingleMixedUseDisposalReturn] =
    (
      ukAddressValidation(disposalDetails.addressDetails),
      disposalDateValidation(disposalDetails.disposalDate),
      countryValidation(desReturn)
    ).mapN {
      case (
            address,
            disposalDate,
            country
          ) =>
        val triageAnswers = constructTriageAnswers(
          desReturn,
          country,
          disposalDate,
          DisposalMethod(disposalDetails.disposalType),
          MixedUse
        )

        val examplePropertyDetailsAnswers = CompleteMixedUsePropertyDetailsAnswers(
          address,
          AmountInPence.fromPounds(disposalDetails.disposalPrice),
          AmountInPence.fromPounds(disposalDetails.acquisitionPrice)
        )

        val exemptionAndLossesAnswers  = constructExemptionAndLossesAnswers(desReturn)
        val yearToDateLiabilityAnswers = constructNonCalculatedYearToDateAnswers(desReturn)
        val gainOrLossAfterReliefs     = constructInitialGainAnswers(disposalDetails)

        CompleteSingleMixedUseDisposalReturn(
          triageAnswers,
          examplePropertyDetailsAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
          None,
          gainOrLossAfterReliefs,
          hasAttachments = desReturn.returnDetails.attachmentUpload
        )
    }

  private def constructNonCalculatedYearToDateAnswers(desReturn: DesReturnDetails): CompleteNonCalculatedYTDAnswers =
    CompleteNonCalculatedYTDAnswers(
      AmountInPence.fromPounds(
        desReturn.returnDetails.totalNetLoss.map(_ * -1).getOrElse(desReturn.returnDetails.totalTaxableGain)
      ),
      desReturn.returnDetails.estimate,
      AmountInPence.fromPounds(desReturn.returnDetails.totalLiability),
      None, // we cannot read the details of the mandatory evidence back
      if isFurtherOrAmendReturn(desReturn) then
        Some(AmountInPence.fromPounds(desReturn.returnDetails.totalYTDLiability))
      else None,
      if isFurtherOrAmendReturn(desReturn) then Some(desReturn.returnDetails.repayment) else None,
      desReturn.incomeAllowanceDetails.estimatedIncome.map(AmountInPence.fromPounds),
      desReturn.incomeAllowanceDetails.personalAllowance.map(AmountInPence.fromPounds)
    )

  private def getIndividualUserType(desReturn: DesReturnDetails): Option[IndividualUserType] =
    desReturn.returnDetails.customerType match {
      case CustomerType.Trust      => None
      case CustomerType.Individual => Some(IndividualUserType.Self)
    }

  private def constructTriageAnswers(
    desReturn: DesReturnDetails,
    country: Country,
    disposalDate: DisposalDate,
    disposalMethod: DisposalMethod,
    assetType: AssetType
  ): CompleteSingleDisposalTriageAnswers =
    CompleteSingleDisposalTriageAnswers(
      getIndividualUserType(desReturn),
      disposalMethod,
      country,
      assetType,
      disposalDate,
      None,
      CompletionDate(desReturn.returnDetails.completionDate)
    )

  private def constructDisposalDetailsAnswers(
    singleDisposalDetails: SingleDisposalDetails
  ): CompleteDisposalDetailsAnswers =
    CompleteDisposalDetailsAnswers(
      ShareOfProperty(singleDisposalDetails.percentOwned),
      AmountInPence.fromPounds(singleDisposalDetails.disposalPrice),
      AmountInPence.fromPounds(singleDisposalDetails.disposalFees)
    )

  private def constructAcquisitionDetailsAnswers(
    singleDisposalDetails: SingleDisposalDetails
  ): CompleteAcquisitionDetailsAnswers =
    CompleteAcquisitionDetailsAnswers(
      AcquisitionMethod(singleDisposalDetails.acquisitionType),
      AcquisitionDate(singleDisposalDetails.acquiredDate),
      AmountInPence.fromPounds(singleDisposalDetails.acquisitionPrice),
      singleDisposalDetails.rebasedAmount.map(AmountInPence.fromPounds),
      zeroOrAmountInPenceFromPounds(singleDisposalDetails.improvementCosts),
      AmountInPence.fromPounds(singleDisposalDetails.acquisitionFees),
      singleDisposalDetails.rebased
    )

  private def constructInitialGainAnswers(
    disposalDetails: DisposalDetails
  ): Option[AmountInPence] = {
    val (initialLoss, initialGain) = disposalDetails match {
      case s: SingleDisposalDetails         => s.initialLoss -> s.initialGain
      case m: MultipleDisposalDetails       => m.initialLoss -> m.initialGain
      case s: SingleMixedUseDisposalDetails => s.initialLoss -> s.initialGain
    }

    (initialLoss, initialGain) match {
      case (Some(loss), _) => Some(AmountInPence.fromPounds(-loss))
      case (_, Some(gain)) => Some(AmountInPence.fromPounds(gain))
      case (None, None)    => None
    }
  }

  private def constructReliefAnswers(
    reliefDetails: ReliefDetails,
    otherReliefsOption: Option[OtherReliefsOption]
  ): CompleteReliefDetailsAnswers =
    CompleteReliefDetailsAnswers(
      zeroOrAmountInPenceFromPounds(reliefDetails.privateResRelief),
      zeroOrAmountInPenceFromPounds(reliefDetails.lettingsRelief),
      otherReliefsOption
    )

  private def constructExemptionAndLossesAnswers(desReturn: DesReturnDetails): CompleteExemptionAndLossesAnswers =
    CompleteExemptionAndLossesAnswers(
      zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.inYearLossUsed),
      zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.preYearLossUsed),
      AmountInPence.fromPounds(desReturn.incomeAllowanceDetails.annualExemption)
    )

  private def countryValidation(desReturn: DesReturnDetails): Validation[Country] =
    if desReturn.returnDetails.isUKResident then Valid(Country.uk)
    else
      desReturn.returnDetails.countryResidence.fold(
        invalid[Country]("Could not find country code for person who was a non-uk resident")
      )(code =>
        if Country.countryCodes.contains(code) then Valid(Country(code))
        else invalid[Country](s"Invalid country code found for person who was a non-uk resident: $code")
      )

  private def reliefsValidation(desReturn: DesReturnDetails): Validation[(ReliefDetails, Option[OtherReliefsOption])] =
    desReturn.reliefDetails.fold(
      invalid[(ReliefDetails, Option[OtherReliefsOption])]("Could not find relief details for single disposal")
    ) { reliefDetails =>
      val otherReliefsOption = (reliefDetails.otherRelief, reliefDetails.otherReliefAmount) match {
        case (None, None)                                               =>
          Valid(None)
        case (Some(other), None)                                        =>
          invalid(s"Found other relief name '$other' but could not find amount'")
        case (None, Some(amount))                                       =>
          invalid(s"Found other relief amount '$amount' but could not find other relief name'")
        case (Some("none"), Some(amount)) if amount === BigDecimal("0") =>
          Valid(Some(OtherReliefsOption.NoOtherReliefs))
        case (Some(name), Some(amount))                                 =>
          Valid(Some(OtherReliefsOption.OtherReliefs(name, AmountInPence.fromPounds(amount))))
      }
      otherReliefsOption.map(reliefDetails -> _)
    }

  private def ukAddressValidation(addressDetails: AddressDetails): Validation[UkAddress] =
    AddressDetails
      .fromDesAddressDetails(addressDetails, allowNonIsoCountryCodes = false)
      .andThen {
        case _: NonUkAddress => invalid("Expected uk address but got non-uk address")
        case a: UkAddress    => Valid(a)
      }

  private def addressValidation(addressDetails: AddressDetails): Validation[Address] =
    AddressDetails
      .fromDesAddressDetails(addressDetails, allowNonIsoCountryCodes = false)

  private def disposalDateValidation(disposalDate: LocalDate): Validation[DisposalDate] =
    taxYearService
      .getTaxYear(disposalDate)
      .fold(
        invalid[DisposalDate](
          s"Could not find tax year for disposal date $disposalDate"
        )
      )(taxYear => Valid(DisposalDate(disposalDate, taxYear)))

  private def assetTypeValidation(singleDisposalDetails: SingleDisposalDetails): Validation[AssetType] =
    singleDisposalDetails.assetType
      .toAssetTypes()
      .flatMap {
        case assetType :: Nil => Right(assetType)
        case other            => Left(s"Expected one asset type for single disposal but got $other")
      }
      .toValidatedNel

  private def assetTypesValidation(
    multipleDisposalDetails: MultipleDisposalDetails
  ): Validation[List[AssetType]] =
    multipleDisposalDetails.assetType
      .toAssetTypes()
      .toValidatedNel

  private def zeroOrAmountInPenceFromPounds(d: Option[BigDecimal]): AmountInPence =
    d.fold(AmountInPence.zero)(AmountInPence.fromPounds)

  /*
    This function determines if a timestamp is present in the source string
    by tokenising it and then counting the number of tokens. Three tokens
    is supposed to indicate a timestamp is present and with some level of
    confidence can be inferred that it is a further return. Otherwise it
    is supposed to indicate that the return is a first return.
   */
  private def isFurtherOrAmendReturn(desReturn: DesReturnDetails): Boolean =
    if desReturn.returnType.source.split(' ').length === 3 then true else false

}
