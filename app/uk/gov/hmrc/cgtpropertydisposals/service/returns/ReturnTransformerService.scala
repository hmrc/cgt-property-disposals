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
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{CustomerType, DesReturnDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CompleteYearToDateLiabilityAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, Validation, invalid}

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
    (
      disposalDetailsValidation(desReturn, taxYearService),
      countryValidation(desReturn),
      otherReliefsValidation(desReturn)
    ).mapN {
      case ((singleDisposalDetails, address, disposalDate), country, otherReliefsOption) =>
        val triageAnswers             = constructTriageAnswers(desReturn, singleDisposalDetails, country, disposalDate)
        val disposalDetailsAnswers    = constructDisposalDetailsAnswers(singleDisposalDetails)
        val acquisitionDetailsAnswers = constructAcquisitionDetailsAnswers(singleDisposalDetails)
        val reliefAnswers             = constructReliefAnswers(desReturn, otherReliefsOption)
        val exemptionAndLossesAnswers = constructExemptionAndLossesAnswers(desReturn)

        val yearToDateLiabilityAnswers = {
          val estimatedIncome =
            zeroOrAmountInPenceFromPounds(desReturn.incomeAllowanceDetails.estimatedIncome)

          val personalAllowance =
            desReturn.incomeAllowanceDetails.personalAllowance.map(AmountInPence.fromPounds)

          CompleteYearToDateLiabilityAnswers(
            estimatedIncome,
            personalAllowance,
            desReturn.returnDetails.estimate,
            cgtCalculationService.calculateTaxDue(
              triageAnswers,
              disposalDetailsAnswers,
              acquisitionDetailsAnswers,
              reliefAnswers,
              exemptionAndLossesAnswers,
              estimatedIncome,
              personalAllowance.getOrElse(AmountInPence.zero)
            ),
            AmountInPence.fromPounds(desReturn.returnDetails.totalLiability),
            None
          )
        }

        CompleteReturn(
          triageAnswers,
          address,
          disposalDetailsAnswers,
          acquisitionDetailsAnswers,
          reliefAnswers,
          exemptionAndLossesAnswers,
          yearToDateLiabilityAnswers
        )
    }

  private def constructTriageAnswers(
    desReturn: DesReturnDetails,
    singleDisposalDetails: SingleDisposalDetails,
    country: Country,
    disposalDate: DisposalDate
  ): CompleteSingleDisposalTriageAnswers = {
    val individualUserType = desReturn.returnDetails.customerType match {
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

    CompleteSingleDisposalTriageAnswers(
      individualUserType,
      DisposalMethod(singleDisposalDetails.disposalType),
      country,
      AssetType(singleDisposalDetails.assetType),
      disposalDate,
      CompletionDate(desReturn.returnDetails.completionDate)
    )
  }

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
      AmountInPence.fromPounds(singleDisposalDetails.acquisitionFees)
    )

  private def constructReliefAnswers(
    desReturn: DesReturnDetails,
    otherReliefsOption: Option[OtherReliefsOption]
  ): CompleteReliefDetailsAnswers =
    CompleteReliefDetailsAnswers(
      zeroOrAmountInPenceFromPounds(desReturn.reliefDetails.privateResRelief),
      zeroOrAmountInPenceFromPounds(desReturn.reliefDetails.lettingsReflief),
      otherReliefsOption
    )

  private def constructExemptionAndLossesAnswers(desReturn: DesReturnDetails): CompleteExemptionAndLossesAnswers = {
    val totalTaxableGainOrNetLoss: AmountInPence =
      (desReturn.returnDetails.totalNetLoss, desReturn.returnDetails.totalTaxableGain) match {
        case (Some(netLoss), _) => AmountInPence.fromPounds(-netLoss)
        case (None, gain)       => AmountInPence.fromPounds(gain)
      }
    CompleteExemptionAndLossesAnswers(
      zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.inYearLossUsed),
      zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.preYearLossUsed),
      AmountInPence.fromPounds(desReturn.incomeAllowanceDetails.annualExemption),
      Some(totalTaxableGainOrNetLoss)
    )
  }

  private def disposalDetailsValidation(
    desReturn: DesReturnDetails,
    taxYearService: TaxYearService
  ): Validation[(SingleDisposalDetails, UkAddress, DisposalDate)] =
    desReturn.disposalDetails match {
      case (singleDisposalDetails: SingleDisposalDetails) :: Nil =>
        AddressDetails
          .fromDesAddressDetails(singleDisposalDetails.addressDetails)(List.empty)
          .andThen {
            case _: NonUkAddress => invalid("Expected uk address but got non-uk address")
            case a: UkAddress    => Valid(singleDisposalDetails -> a)
          }
          .andThen {
            case (singleDisposalDetails, address) =>
              val disposalDate = singleDisposalDetails.disposalDate
              taxYearService
                .getTaxYear(disposalDate)
                .fold(
                  invalid[(SingleDisposalDetails, UkAddress, DisposalDate)](
                    s"Could not find tax year for disposal date $disposalDate"
                  )
                )(taxYear => Valid((singleDisposalDetails, address, DisposalDate(disposalDate, taxYear))))
          }

      case (_: MultipleDisposalDetails) :: Nil =>
        invalid("Multiple disposals not handled yet")

      case other =>
        Invalid(
          NonEmptyList.one(
            s"Expected either one single disposal detail or one multiple disposal details but got ${other.length}"
          )
        )
    }

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

  private def otherReliefsValidation(desReturn: DesReturnDetails): Validation[Option[OtherReliefsOption]] =
    (desReturn.reliefDetails.otherRelief, desReturn.reliefDetails.otherReliefAmount) match {
      case (None, None)        => Valid(None)
      case (Some(other), None) => invalid(s"Found other relief name '$other' but could not find amount'")
      case (None, Some(amount)) =>
        invalid(s"Found other relief amount '$amount' but could not find other relief name'")
      case (Some("none"), Some(amount)) if amount === BigDecimal("0") =>
        Valid(Some(OtherReliefsOption.NoOtherReliefs))
      case (Some(name), Some(amount)) =>
        Valid(Some(OtherReliefsOption.OtherReliefs(name, AmountInPence.fromPounds(amount))))
    }

  private def zeroOrAmountInPenceFromPounds(d: Option[BigDecimal]): AmountInPence =
    d.fold(AmountInPence.zero)(AmountInPence.fromPounds)

}
