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

package uk.gov.hmrc.cgtpropertydisposals.service.returns.transformers

import java.time.{LocalDate, LocalDateTime}

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.bigDecimal._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.CustomerType.Trust
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{CustomerType, DesReturnDetails, ReliefDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteSingleDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExamplePropertyDetailsAnswers.CompleteExamplePropertyDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.MultipleDisposalsTriageAnswers.CompleteMultipleDisposalsTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SupportingEvidenceAnswers.CompleteSupportingEvidenceAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, Validation, invalid}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{CgtCalculationService, TaxYearService}

@ImplementedBy(classOf[ReturnTransformerServiceImpl])
trait ReturnTransformerService {

  def toCompleteReturn(desReturn: DesReturnDetails): Either[Error, CompleteReturn]

}

@Singleton
class ReturnTransformerServiceImpl @Inject() (
  cgtCalculationService: CgtCalculationService,
  taxYearService: TaxYearService
) extends ReturnTransformerService {

  override def toCompleteReturn(desReturn: DesReturnDetails): Either[Error, CompleteReturn] =
    fromDesReturn(desReturn).toEither
      .leftMap(e => Error(s"Could not convert des response to complete return: [${e.toList.mkString("; ")}]"))

  private def fromDesReturn(
    desReturn: DesReturnDetails
  ): ValidatedNel[String, CompleteReturn] =
    desReturn.disposalDetails match {
      case (singleDisposalDetails: SingleDisposalDetails) :: Nil =>
        validateSingleDisposal(desReturn, singleDisposalDetails)

      case (multipleDisposalsDetails: MultipleDisposalDetails) :: Nil =>
        validateMultipleDisposal(desReturn, multipleDisposalsDetails)

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
          disposalDate.taxYear,
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

        CompleteMultipleDisposalsReturn(
          triageAnswers,
          examplePropertyDetailsAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
          hasAttachments = desReturn.returnDetails.attachmentUpload
        )
    }

  private def validateSingleDisposal(
    desReturn: DesReturnDetails,
    singleDisposalDetails: SingleDisposalDetails
  ): ValidatedNel[String, CompleteSingleDisposalReturn] =
    (
      addressValidation(singleDisposalDetails.addressDetails),
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
        val triageAnswers =
          constructTriageAnswers(
            desReturn,
            singleDisposalDetails,
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

        val yearToDateLiabilityAnswers = otherReliefsOption match {
          case Some(_: OtherReliefsOption.OtherReliefs) =>
            Left(constructNonCalculatedYearToDateAnswers(desReturn))

          case _ =>
            val estimatedIncome =
              zeroOrAmountInPenceFromPounds(desReturn.incomeAllowanceDetails.estimatedIncome)

            val personalAllowance =
              desReturn.incomeAllowanceDetails.personalAllowance.map(AmountInPence.fromPounds)
            val calculatedTaxDue = cgtCalculationService.calculateTaxDue(
              triageAnswers,
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
          initialGainOrLoss,
          CompleteSupportingEvidenceAnswers(false, List.empty), // we cannot determine if they uploaded anything
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
      MandatoryEvidence("", "", LocalDateTime.now()) // we cannot read the details of the mandatory evidence back
    )

  private def getIndividualUserType(desReturn: DesReturnDetails): Option[IndividualUserType] =
    desReturn.returnDetails.customerType match {
      case CustomerType.Trust =>
        None
      case CustomerType.Individual =>
        Some(
          desReturn.representedPersonDetails.fold[IndividualUserType](
            IndividualUserType.Self
          )(
            _.dateOfDeath.fold[IndividualUserType](IndividualUserType.Capacitor)(_ =>
              IndividualUserType.PersonalRepresentative
            )
          )
        )
    }
  private def constructTriageAnswers(
    desReturn: DesReturnDetails,
    singleDisposalDetails: SingleDisposalDetails,
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
    singleDisposalDetails: SingleDisposalDetails
  ): Option[AmountInPence] =
    (singleDisposalDetails.initialLoss, singleDisposalDetails.initialGain) match {
      case (Some(loss), _) => Some(AmountInPence.fromPounds(-loss))
      case (_, Some(gain)) => Some(AmountInPence.fromPounds(gain))
      case (None, None)    => None
    }

  private def constructReliefAnswers(
    reliefDetails: ReliefDetails,
    otherReliefsOption: Option[OtherReliefsOption]
  ): CompleteReliefDetailsAnswers =
    CompleteReliefDetailsAnswers(
      zeroOrAmountInPenceFromPounds(reliefDetails.privateResRelief),
      zeroOrAmountInPenceFromPounds(reliefDetails.lettingsReflief),
      otherReliefsOption
    )

  private def constructExemptionAndLossesAnswers(desReturn: DesReturnDetails): CompleteExemptionAndLossesAnswers =
    CompleteExemptionAndLossesAnswers(
      zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.inYearLossUsed),
      zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.preYearLossUsed),
      AmountInPence.fromPounds(desReturn.incomeAllowanceDetails.annualExemption)
    )

  private def countryValidation(desReturn: DesReturnDetails): Validation[Country] =
    if (desReturn.returnDetails.isUKResident) Valid(Country.uk)
    else
      desReturn.returnDetails.countryResidence.fold(
        invalid[Country]("Could not find country code for person who was a non-uk resident")
      )(code =>
        Country.countryCodeToCountryName
          .get(code)
          .fold(
            invalid[Country](s"Invalid country code found for person who was a non-uk resident: $code")
          )(name => Valid(Country(code, Some(name))))
      )

  private def reliefsValidation(desReturn: DesReturnDetails): Validation[(ReliefDetails, Option[OtherReliefsOption])] =
    desReturn.reliefDetails.fold(
      invalid[(ReliefDetails, Option[OtherReliefsOption])]("Could not find relief details for single disposal")
    ) { reliefDetails =>
      val otherReliefsOption = (reliefDetails.otherRelief, reliefDetails.otherReliefAmount) match {
        case (None, None) =>
          Valid(None)
        case (Some(other), None) =>
          invalid(s"Found other relief name '$other' but could not find amount'")
        case (None, Some(amount)) =>
          invalid(s"Found other relief amount '$amount' but could not find other relief name'")
        case (Some("none"), Some(amount)) if amount === BigDecimal("0") =>
          Valid(Some(OtherReliefsOption.NoOtherReliefs))
        case (Some(name), Some(amount)) =>
          Valid(Some(OtherReliefsOption.OtherReliefs(name, AmountInPence.fromPounds(amount))))
      }
      otherReliefsOption.map(reliefDetails -> _)
    }

  private def addressValidation(addressDetails: AddressDetails): Validation[UkAddress] =
    AddressDetails
      .fromDesAddressDetails(addressDetails)(List.empty)
      .andThen {
        case _: NonUkAddress => invalid("Expected uk address but got non-uk address")
        case a: UkAddress    => Valid(a)
      }

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

  private def assetTypesValidation(multipleDisposalDetails: MultipleDisposalDetails): Validation[List[AssetType]] =
    multipleDisposalDetails.assetType
      .toAssetTypes()
      .toValidatedNel

  private def zeroOrAmountInPenceFromPounds(d: Option[BigDecimal]): AmountInPence =
    d.fold(AmountInPence.zero)(AmountInPence.fromPounds)

}
