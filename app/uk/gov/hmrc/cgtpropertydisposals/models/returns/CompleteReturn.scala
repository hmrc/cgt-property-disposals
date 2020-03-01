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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import java.time.LocalDate

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.bigDecimal._
import cats.syntax.apply._
import cats.syntax.eq._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.PPDReturnDetails
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CompleteYearToDateLiabilityAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.{TaxYear, Validation, invalid}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.CgtCalculationService
final case class CompleteReturn(
  triageAnswers: CompleteSingleDisposalTriageAnswers,
  propertyAddress: UkAddress,
  disposalDetails: CompleteDisposalDetailsAnswers,
  acquisitionDetails: CompleteAcquisitionDetailsAnswers,
  reliefDetails: CompleteReliefDetailsAnswers,
  exemptionsAndLossesDetails: CompleteExemptionAndLossesAnswers,
  yearToDateLiabilityAnswers: CompleteYearToDateLiabilityAnswers
)

object CompleteReturn {

  def fromDesResponse(
    desReturn: PPDReturnDetails,
    calculationService: CgtCalculationService
  ): ValidatedNel[String, CompleteReturn] = {
    val totalTaxableGainOrNetLoss: AmountInPence =
      (desReturn.returnDetails.totalNetLoss, desReturn.returnDetails.totalTaxableGain) match {
        case (Some(netLoss), _) => AmountInPence.fromPounds(-netLoss)
        case (None, gain)       => AmountInPence.fromPounds(gain)
      }

    (disposalDetailsValidation(desReturn), countryValidation(desReturn), otherReliefsValidation(desReturn)).mapN {
      case ((singleDisposalDetails, address), country, otherReliefsOption) =>
        val triageAnswers =
          CompleteSingleDisposalTriageAnswers(
            desReturn.representedPersonDetails.fold[IndividualUserType](
              IndividualUserType.Self
            )(
              _.dateOfDeath.fold[IndividualUserType](IndividualUserType.Capacitor)(_ =>
                IndividualUserType.PersonalRepresentative
              )
            ),
            NumberOfProperties.One,
            DisposalMethod(singleDisposalDetails.disposalType),
            country,
            AssetType(singleDisposalDetails.assetType),
            DisposalDate(
              singleDisposalDetails.disposalDate,
              TaxYear(
                LocalDate.now(),
                LocalDate.now(),
                AmountInPence.zero,
                AmountInPence.zero,
                AmountInPence.zero,
                AmountInPence.zero,
                0,
                0,
                0,
                0
              )
            ),
            CompletionDate(desReturn.returnDetails.completionDate)
          )

        val disposalDetailsAnswers =
          CompleteDisposalDetailsAnswers(
            ShareOfProperty(singleDisposalDetails.percentOwned),
            AmountInPence.fromPounds(singleDisposalDetails.disposalPrice),
            AmountInPence.fromPounds(singleDisposalDetails.disposalFees)
          )

        val acquisitionDetailsAnswers =
          CompleteAcquisitionDetailsAnswers(
            AcquisitionMethod(singleDisposalDetails.acquisitionType),
            AcquisitionDate(singleDisposalDetails.acquiredDate),
            AmountInPence.fromPounds(singleDisposalDetails.acquisitionPrice),
            singleDisposalDetails.rebasedAmount.map(AmountInPence.fromPounds),
            zeroOrAmountInPenceFromPounds(singleDisposalDetails.improvementCosts),
            AmountInPence.fromPounds(singleDisposalDetails.acquisitionFees)
          )

        val reliefAnswers =
          CompleteReliefDetailsAnswers(
            zeroOrAmountInPenceFromPounds(desReturn.reliefDetails.privateResRelief),
            zeroOrAmountInPenceFromPounds(desReturn.reliefDetails.lettingsReflief),
            otherReliefsOption
          )

        val exemptionAndLossesAnswers =
          CompleteExemptionAndLossesAnswers(
            zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.inYearLossUsed),
            zeroOrAmountInPenceFromPounds(desReturn.lossSummaryDetails.preYearLossUsed),
            AmountInPence.fromPounds(desReturn.incomeAllowanceDetails.annualExemption),
            Some(totalTaxableGainOrNetLoss)
          )

        val yearToDateLiabilityAnswers = {
          val estimatedIncome =
            zeroOrAmountInPenceFromPounds(desReturn.incomeAllowanceDetails.estimatedIncome)

          val personalAllowance =
            desReturn.incomeAllowanceDetails.personalAllowance.map(AmountInPence.fromPounds)

          CompleteYearToDateLiabilityAnswers(
            estimatedIncome,
            personalAllowance,
            desReturn.returnDetails.estimate,
            calculationService.calculateTaxDue(
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
  }

  private def disposalDetailsValidation(desReturn: PPDReturnDetails): Validation[(SingleDisposalDetails, UkAddress)] =
    desReturn.disposalDetails match {
      case (singleDisposalDetails: SingleDisposalDetails) :: Nil =>
        AddressDetails
          .fromDesAddressDetails(singleDisposalDetails.addressDetails)(List.empty)
          .andThen {
            case _: NonUkAddress => invalid("Expected uk address but got non-uk address")
            case a: UkAddress    => Valid(singleDisposalDetails -> a)
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

  private def countryValidation(desReturn: PPDReturnDetails): Validation[Country] =
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

  private def otherReliefsValidation(desReturn: PPDReturnDetails): Validation[Option[OtherReliefsOption]] =
    (desReturn.reliefDetails.otherRelief, desReturn.reliefDetails.otherReliefAmount) match {
      case (None, None)        => Valid(None)
      case (Some(other), None) => invalid(s"Found other relief name '$other' but could not find amount'")
      case (None, Some(amount)) =>
        invalid(s"Found other relief amount '$amount' but could not find other relief name'")
      case (Some("none"), Some(amount)) if (amount === BigDecimal("0")) =>
        Valid(Some(OtherReliefsOption.NoOtherReliefs))
      case (Some(name), Some(amount)) =>
        Valid(Some(OtherReliefsOption.OtherReliefs(name, AmountInPence.fromPounds(amount))))
    }

  private def zeroOrAmountInPenceFromPounds(d: Option[BigDecimal]): AmountInPence =
    d.fold(AmountInPence.zero)(AmountInPence.fromPounds)

  implicit val format: OFormat[CompleteReturn] = {
    implicit val triageFormat: OFormat[CompleteSingleDisposalTriageAnswers]             = Json.format
    implicit val ukAddressFormat: OFormat[UkAddress]                                    = Json.format
    implicit val disposalDetailsFormat: OFormat[CompleteDisposalDetailsAnswers]         = Json.format
    implicit val acquisitionDetailsFormat: OFormat[CompleteAcquisitionDetailsAnswers]   = Json.format
    implicit val reliefDetailsFormat: OFormat[CompleteReliefDetailsAnswers]             = Json.format
    implicit val exemptionAndLossesFormat: OFormat[CompleteExemptionAndLossesAnswers]   = Json.format
    implicit val yearToDateLiabilityFormat: OFormat[CompleteYearToDateLiabilityAnswers] = Json.format
    Json.format
  }
}
