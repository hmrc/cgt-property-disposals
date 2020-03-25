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

import java.time.LocalDate
import cats.syntax.either._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.MultipleDisposalDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{CustomerType, DesAcquisitionType, DesAssetType, DesDisposalType, DesReturnDetails, DisposalDetails, ReliefDetails, RepresentedPersonDetails, ReturnDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AcquisitionDate, AcquisitionMethod, AssetType, CalculatedTaxDue, CompletionDate, DisposalDate, DisposalMethod, IndividualUserType, OtherReliefsOption, ShareOfProperty}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{CgtCalculationService, TaxYearService}

class ReturnTransformerServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockCalculationService = mock[CgtCalculationService]

  val mockTaxYearService = mock[TaxYearService]

  val transformer = new ReturnTransformerServiceImpl(mockCalculationService, mockTaxYearService)

  def mockCalculateTaxDue()(result: CalculatedTaxDue) =
    (
      mockCalculationService
        .calculateTaxDue(
          _: CompleteSingleDisposalTriageAnswers,
          _: CompleteDisposalDetailsAnswers,
          _: CompleteAcquisitionDetailsAnswers,
          _: CompleteReliefDetailsAnswers,
          _: CompleteExemptionAndLossesAnswers,
          _: AmountInPence,
          _: AmountInPence,
          _: Option[AmountInPence],
          _: Boolean
        )
      )
      .expects(*, *, *, *, *, *, *, *, *)
      .returning(result)

  def mockGetTaxYear(date: LocalDate)(result: Option[TaxYear]) =
    (mockTaxYearService
      .getTaxYear(_: LocalDate))
      .expects(date)
      .returning(result)

  "ReturnTransformerServiceImpl" when {

    val ukAddress = sample[UkAddress]

    val taxYear = sample[TaxYear]

    val calculatedTaxDue = sample[CalculatedTaxDue]

    val validSingleDisposalDetails = sample[DisposalDetails.SingleDisposalDetails].copy(
      addressDetails = Address.toAddressDetails(ukAddress)
    )

    "passed details of a single disposal" must {

      val validIndividualSingleDisposalDesReturnDetails = sample[DesReturnDetails].copy(
        disposalDetails = List(validSingleDisposalDetails),
        returnDetails   = sample[ReturnDetails].copy(isUKResident = true, customerType = CustomerType.Individual),
        reliefDetails   = sample[ReliefDetails].copy(otherRelief = None, otherReliefAmount = None)
      )

      def mockActions(): Unit =
        inSequence {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))
          mockCalculateTaxDue()(calculatedTaxDue)
        }

      "return an error" when {

        "the address is a non uk address" in {
          val desReturn = validIndividualSingleDisposalDesReturnDetails.copy(
            disposalDetails = List(
              validSingleDisposalDetails.copy(addressDetails = Address.toAddressDetails(sample[NonUkAddress]))
            )
          )

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "a tax year cannot be found for the disposal date" in {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(None)

          transformer.toCompleteReturn(validIndividualSingleDisposalDesReturnDetails).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident cannot be found" in {
          val desReturn = validIndividualSingleDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident     = false,
              countryResidence = None
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident is not recognised" in {
          val desReturn = validIndividualSingleDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident     = false,
              countryResidence = Some("????")
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "there is an 'other relief' and a name can be found but an amount cannot" in {
          val desReturn = validIndividualSingleDisposalDesReturnDetails.copy(
            reliefDetails = sample[ReliefDetails].copy(
              otherRelief       = Some("name"),
              otherReliefAmount = None
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "there is an 'other relief' and an amount can be found but a name cannot" in {
          val desReturn = validIndividualSingleDisposalDesReturnDetails.copy(
            reliefDetails = sample[ReliefDetails].copy(
              otherRelief       = None,
              otherReliefAmount = Some(BigDecimal(1))
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

      }

      "transform triage answers correctly" when {

        "there are no represented personal details" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              representedPersonDetails = None
            )
          )

          result.map(_.triageAnswers.individualUserType) shouldBe Right(Some(IndividualUserType.Self))
        }

        "there are represented personal details but no date of death" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = None
                )
              )
            )
          )

          result.map(_.triageAnswers.individualUserType) shouldBe Right(Some(IndividualUserType.Capacitor))
        }

        "there are represented personal details with a date of death" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = Some("2000-01-01")
                )
              )
            )
          )

          result.map(_.triageAnswers.individualUserType) shouldBe Right(Some(IndividualUserType.PersonalRepresentative))
        }

        "the user was a trust" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              returnDetails = validIndividualSingleDisposalDesReturnDetails.returnDetails.copy(
                customerType = CustomerType.Trust
              )
            )
          )

          result.map(_.triageAnswers.individualUserType) shouldBe Right(None)
        }

        "the user was a uk resident" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = true
              )
            )
          )

          result.map(_.triageAnswers.countryOfResidence) shouldBe Right(Country.uk)
        }

        "the user was not a uk resident and there is a valid country code" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident     = false,
                countryResidence = Some("HK")
              )
            )
          )

          result.map(_.triageAnswers.countryOfResidence) shouldBe Right(Country("HK", Some("Hong Kong")))
        }

        "finding the disposal method" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  disposalType = DesDisposalType.Sold
                )
              )
            )
          )

          result.map(_.triageAnswers.disposalMethod) shouldBe Right(DisposalMethod.Sold)
        }

        "finding the asset type" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  assetType = DesAssetType.NonResidential
                )
              )
            )
          )

          result.map(_.triageAnswers.assetType) shouldBe Right(AssetType.NonResidential)
        }

        "a tax year can be found for the disposal date" in {
          mockActions()

          val result = transformer.toCompleteReturn(validIndividualSingleDisposalDesReturnDetails)

          result.map(_.triageAnswers.disposalDate) shouldBe Right(
            DisposalDate(validSingleDisposalDetails.disposalDate, taxYear)
          )
        }

        "finding the completion data" in {
          val completionDate = LocalDate.now()
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              returnDetails = validIndividualSingleDisposalDesReturnDetails.returnDetails.copy(
                completionDate = completionDate
              )
            )
          )

          result.map(_.triageAnswers.completionDate) shouldBe Right(CompletionDate(completionDate))
        }

      }

      "transform disposal details answers correctly" when {

        "finding the share of the property" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  percentOwned = BigDecimal("100")
                )
              )
            )
          )

          result.map(_.disposalDetails.shareOfProperty) shouldBe Right(ShareOfProperty.Full)
        }

        "finding the disposal price" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  disposalPrice = BigDecimal("123.45")
                )
              )
            )
          )

          result.map(_.disposalDetails.disposalPrice) shouldBe Right(AmountInPence(12345L))
        }

        "finding the disposal fees" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  disposalFees = BigDecimal("123.45")
                )
              )
            )
          )

          result.map(_.disposalDetails.disposalFees) shouldBe Right(AmountInPence(12345L))
        }

      }

      "transform acquisition details answers correctly" when {

        "finding the acquisition method" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquisitionType = DesAcquisitionType.Inherited
                )
              )
            )
          )

          result.map(_.acquisitionDetails.acquisitionMethod) shouldBe Right(AcquisitionMethod.Inherited)
        }

        "finding the acquisition date" in {
          val date = LocalDate.now()
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquiredDate = date
                )
              )
            )
          )

          result.map(_.acquisitionDetails.acquisitionDate) shouldBe Right(AcquisitionDate(date))
        }

        "finding the acquisition price" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquisitionPrice = BigDecimal("12345")
                )
              )
            )
          )

          result.map(_.acquisitionDetails.acquisitionPrice) shouldBe Right(AmountInPence(1234500L))
        }

        "finding the rebased amount when one is defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  rebasedAmount = Some(BigDecimal("12345"))
                )
              )
            )
          )

          result.map(_.acquisitionDetails.rebasedAcquisitionPrice) shouldBe Right(Some(AmountInPence(1234500L)))
        }

        "there is no rebased amount" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  rebasedAmount = None
                )
              )
            )
          )

          result.map(_.acquisitionDetails.rebasedAcquisitionPrice) shouldBe Right(None)
        }

        "the improvement costs are defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  improvementCosts = Some(BigDecimal("1.23"))
                )
              )
            )
          )

          result.map(_.acquisitionDetails.improvementCosts) shouldBe Right(AmountInPence(123L))
        }

        "the improvement costs are not defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  improvementCosts = None
                )
              )
            )
          )

          result.map(_.acquisitionDetails.improvementCosts) shouldBe Right(AmountInPence(0L))
        }

        "finding the acquisition fees" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquisitionFees = BigDecimal("1.23")
                )
              )
            )
          )

          result.map(_.acquisitionDetails.acquisitionFees) shouldBe Right(AmountInPence(123L))
        }

      }

      "transform relief answers correctly" when {

        "the private residents relief is defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                privateResRelief = Some(BigDecimal("123.45"))
              )
            )
          )

          result.map(_.reliefDetails.privateResidentsRelief) shouldBe Right(AmountInPence(12345L))
        }

        "the private residents relief is not defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                privateResRelief = None
              )
            )
          )

          result.map(_.reliefDetails.privateResidentsRelief) shouldBe Right(AmountInPence.zero)
        }

        "the letting relief is defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                lettingsReflief = Some(BigDecimal("123.45"))
              )
            )
          )

          result.map(_.reliefDetails.lettingsRelief) shouldBe Right(AmountInPence(12345L))
        }

        "the letting  relief is not defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                lettingsReflief = None
              )
            )
          )

          result.map(_.reliefDetails.lettingsRelief) shouldBe Right(AmountInPence.zero)
        }

        "there is no other relief option defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                otherRelief       = None,
                otherReliefAmount = None
              )
            )
          )

          result.map(_.reliefDetails.otherReliefs) shouldBe Right(None)
        }

        "the no other reliefs indicate the user selected no other reliefs" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                otherRelief       = Some("none"),
                otherReliefAmount = Some(BigDecimal(0))
              )
            )
          )

          result.map(_.reliefDetails.otherReliefs) shouldBe Right(Some(OtherReliefsOption.NoOtherReliefs))
        }

        "the other reliefs answers indicate the user selected other reliefs" in {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              reliefDetails = validIndividualSingleDisposalDesReturnDetails.reliefDetails.copy(
                otherRelief       = Some("abc"),
                otherReliefAmount = Some(BigDecimal("12.34"))
              )
            )
          )

          result.map(_.reliefDetails.otherReliefs) shouldBe Right(
            Some(OtherReliefsOption.OtherReliefs("abc", AmountInPence(1234L)))
          )
        }

      }

      "transform exemption and losses answers correctly" when {

        "the in year losses is defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validIndividualSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          result.map(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(AmountInPence(12345L))
        }

        "the in year losses is not defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validIndividualSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = None
              )
            )
          )

          result.map(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(AmountInPence.zero)
        }

        "the previous year losses is defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validIndividualSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          result.map(_.exemptionsAndLossesDetails.previousYearsLosses) shouldBe Right(AmountInPence(12345L))
        }

        "the previous year losses is not defined" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validIndividualSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = None
              )
            )
          )

          result.map(_.exemptionsAndLossesDetails.previousYearsLosses) shouldBe Right(AmountInPence.zero)
        }

        "finding the annual exempt amount" in {
          mockActions()

          val result = transformer.toCompleteReturn(
            validIndividualSingleDisposalDesReturnDetails.copy(
              incomeAllowanceDetails = validIndividualSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                annualExemption = BigDecimal("12.34")
              )
            )
          )

          result.map(_.exemptionsAndLossesDetails.annualExemptAmount) shouldBe Right(AmountInPence(1234L))
        }

      }

      "transform year to date liability answers correctly" when {

        "the user has not selected other reliefs and" when {

          "the estimated income is defined" in {
            mockActions()

            val result = transformer.toCompleteReturn(
              validIndividualSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validIndividualSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  estimatedIncome = Some(BigDecimal("123.45"))
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.map(_.estimatedIncome)) shouldBe Right(Right(AmountInPence(12345L)))
          }

          "the estimated income is not defined" in {
            mockActions()

            val result = transformer.toCompleteReturn(
              validIndividualSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validIndividualSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  estimatedIncome = None
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.map(_.estimatedIncome)) shouldBe Right(Right(AmountInPence.zero))
          }

          "the personal allowance is defined" in {
            mockActions()

            val result = transformer.toCompleteReturn(
              validIndividualSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validIndividualSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  personalAllowance = Some(BigDecimal("123.45"))
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.map(_.personalAllowance)) shouldBe Right(
              Right(Some(AmountInPence(12345L)))
            )
          }

          "the personal is not defined" in {
            mockActions()

            val result = transformer.toCompleteReturn(
              validIndividualSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validIndividualSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  personalAllowance = None
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.map(_.personalAllowance)) shouldBe Right(Right(None))
          }

          "finding whether any of the details were estimated" in {
            mockActions()

            val result = transformer.toCompleteReturn(
              validIndividualSingleDisposalDesReturnDetails.copy(
                returnDetails = validIndividualSingleDisposalDesReturnDetails.returnDetails.copy(
                  estimate = true
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.map(_.hasEstimatedDetails)) shouldBe Right(Right(true))
          }

          "getting the calculated tax due" in {
            mockActions()

            val result = transformer.toCompleteReturn(validIndividualSingleDisposalDesReturnDetails)

            result.map(_.yearToDateLiabilityAnswers.map(_.calculatedTaxDue)) shouldBe Right(Right(calculatedTaxDue))
          }

          "finding the tax due" in {
            mockActions()

            val result = transformer.toCompleteReturn(
              validIndividualSingleDisposalDesReturnDetails.copy(
                returnDetails = validIndividualSingleDisposalDesReturnDetails.returnDetails.copy(
                  totalLiability = BigDecimal("12345.67")
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.map(_.taxDue)) shouldBe Right(Right(AmountInPence(1234567L)))
          }

        }

        "the user has selected other reliefs and" when {

          val returnDetailsWithOtherReliefs = validIndividualSingleDisposalDesReturnDetails.copy(
            reliefDetails =
              sample[ReliefDetails].copy(otherRelief = Some("other"), otherReliefAmount = Some(BigDecimal("100")))
          )

          "finding the taxableGainOrLoss when a loss has been made" in {
            mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

            val result = transformer.toCompleteReturn(
              returnDetailsWithOtherReliefs.copy(
                returnDetails = returnDetailsWithOtherReliefs.returnDetails.copy(
                  totalNetLoss     = Some(BigDecimal("1")),
                  totalTaxableGain = BigDecimal("0")
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.leftMap(_.taxableGainOrLoss)) shouldBe Right(
              Left(AmountInPence(-100L))
            )
          }

          "finding the taxableGainOrLoss when a gain has been made" in {
            mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

            val result = transformer.toCompleteReturn(
              returnDetailsWithOtherReliefs.copy(
                returnDetails = returnDetailsWithOtherReliefs.returnDetails.copy(
                  totalNetLoss     = None,
                  totalTaxableGain = BigDecimal("2")
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.leftMap(_.taxableGainOrLoss)) shouldBe Right(
              Left(AmountInPence(200L))
            )
          }

          "finding whether a user has estimated any details" in {
            mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

            val result = transformer.toCompleteReturn(
              returnDetailsWithOtherReliefs.copy(
                returnDetails = returnDetailsWithOtherReliefs.returnDetails.copy(
                  estimate = true
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.leftMap(_.hasEstimatedDetails)) shouldBe Right(Left(true))
          }

          "finding the tax due for the user" in {
            mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

            val result = transformer.toCompleteReturn(
              returnDetailsWithOtherReliefs.copy(
                returnDetails = returnDetailsWithOtherReliefs.returnDetails.copy(
                  totalLiability = BigDecimal("3")
                )
              )
            )

            result.map(_.yearToDateLiabilityAnswers.leftMap(_.taxDue)) shouldBe Right(Left(AmountInPence(300L)))

          }

        }

      }

    }

    "passed details of a multiple disposal" must {

      "return an error" in {
        val result = transformer.toCompleteReturn(
          sample[DesReturnDetails].copy(
            disposalDetails = List(sample[MultipleDisposalDetails])
          )
        )

        result.isLeft shouldBe true
      }

    }

    "passed details of more than one disposal" must {

      "return an error" in {
        val result = transformer.toCompleteReturn(
          sample[DesReturnDetails].copy(
            disposalDetails = List(validSingleDisposalDetails, validSingleDisposalDetails)
          )
        )

        result.isLeft shouldBe true

      }

    }

  }

}
