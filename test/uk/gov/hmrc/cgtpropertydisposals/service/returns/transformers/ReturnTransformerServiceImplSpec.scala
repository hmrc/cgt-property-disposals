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
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails, SingleMixedUseDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxYear}
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

    val nonUkAddress = sample[NonUkAddress].copy(country = sample[Country])

    val taxYear = sample[TaxYear]

    val calculatedTaxDue = sample[CalculatedTaxDue]

    val validSingleDisposalDetails = sample[SingleDisposalDetails].copy(
      addressDetails = Address.toAddressDetails(ukAddress),
      disposalType = DesDisposalType.Sold,
      assetType = DesAssetTypeValue("res")
    )

    val validMultipleDisposalDetails = sample[MultipleDisposalDetails].copy(
      addressDetails = Address.toAddressDetails(ukAddress),
      assetType = DesAssetTypeValue("res shares")
    )

    val validSingleIndirectDisposalDetails = sample[SingleDisposalDetails].copy(
      addressDetails = Address.toAddressDetails(ukAddress),
      disposalType = DesDisposalType.Sold,
      assetType = DesAssetTypeValue("shares")
    )

    val validMultipleIndirectDisposalDetails = sample[MultipleDisposalDetails].copy(
      addressDetails = Address.toAddressDetails(ukAddress),
      assetType = DesAssetTypeValue("shares")
    )

    val validSingleMixedUseDisposalDetails = sample[SingleMixedUseDisposalDetails].copy(
      addressDetails = Address.toAddressDetails(ukAddress),
      assetType = DesAssetTypeValue("mix")
    )

    "passed details of a single disposal" must {

      val validReliefDetails = sample[ReliefDetails].copy(otherRelief = None, otherReliefAmount = None)

      val validSingleDisposalDesReturnDetails = sample[DesReturnDetails].copy(
        disposalDetails = List(validSingleDisposalDetails),
        returnDetails = sample[ReturnDetails].copy(isUKResident = true, customerType = CustomerType.Individual),
        reliefDetails = Some(validReliefDetails)
      )

      def completeSingleDisposalReturnValue[A](
        result: Either[Error, DisplayReturn]
      )(value: CompleteSingleDisposalReturn => A): Either[String, A] =
        result match {
          case Left(e)                 => Left(s"Expected CompleteSingleDisposalReturn but got $e")
          case Right(d: DisplayReturn) =>
            d.completeReturn match {
              case s @ CompleteSingleDisposalReturn(_, _, _, _, _, _, _, _, _, _, _, _) => Right(value(s))
              case other                                                                => Left(s"Expected CompleteSingleDisposalReturn but got $other")
            }
          case Right(other)            => Left(s"Expected CompleteSingleDisposalReturn but got $other")
        }

      def mockGetTaxYearAndCalculatedTaxDue(): Unit =
        inSequence {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))
          mockCalculateTaxDue()(calculatedTaxDue)
        }

      "return an error" when {

        "the address is a non uk address" in {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

          val desReturn = validSingleDisposalDesReturnDetails.copy(
            disposalDetails = List(
              validSingleDisposalDetails.copy(addressDetails = Address.toAddressDetails(sample[NonUkAddress]))
            )
          )

          val result = transformer.toCompleteReturn(desReturn)
          result.isLeft shouldBe true
        }

        "a tax year cannot be found for the disposal date" in {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(None)

          transformer.toCompleteReturn(validSingleDisposalDesReturnDetails).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident cannot be found" in {
          val desReturn = validSingleDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = None
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident is not recognised" in {
          val desReturn = validSingleDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = Some("????")
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "there is an 'other relief' and a name can be found but an amount cannot" in {
          val desReturn = validSingleDisposalDesReturnDetails.copy(
            reliefDetails = Some(
              sample[ReliefDetails].copy(
                otherRelief = Some("name"),
                otherReliefAmount = None
              )
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "there is an 'other relief' and an amount can be found but a name cannot" in {
          val desReturn = validSingleDisposalDesReturnDetails.copy(
            reliefDetails = Some(
              sample[ReliefDetails].copy(
                otherRelief = None,
                otherReliefAmount = Some(BigDecimal(1))
              )
            )
          )

          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "no relief details can be found" in {
          val desReturn = validSingleDisposalDesReturnDetails.copy(
            reliefDetails = None
          )
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(sample[TaxYear]))

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

      }

      "transform triage answers correctly" when {

        "there are no represented personal details" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              representedPersonDetails = None
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(
              IndividualUserType.Self
            )
          )
        }

        "there are represented personal details but no date of death" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = None
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "there are represented personal details with a date of death" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = Some(LocalDate.of(2000, 1, 1))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "the user was a trust" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              returnDetails = validSingleDisposalDesReturnDetails.returnDetails.copy(
                customerType = CustomerType.Trust
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(None)
        }

        "the user was a uk resident" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = true
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(Country.uk)
        }

        "the user was not a uk resident and there is a valid country code" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = false,
                countryResidence = Some("HK")
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country("HK")
          )
        }

        "finding the disposal method" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  disposalType = DesDisposalType.Sold
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.disposalMethod) shouldBe Right(DisposalMethod.Sold)
        }

        "finding the asset type" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  assetType = DesAssetTypeValue("nonres")
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.assetType) shouldBe Right(AssetType.NonResidential)
        }

        "a tax year can be found for the disposal date" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(validSingleDisposalDesReturnDetails)

          completeSingleDisposalReturnValue(result)(_.triageAnswers.disposalDate) shouldBe Right(
            DisposalDate(validSingleDisposalDetails.disposalDate, taxYear)
          )
        }

        "finding the completion data" in {
          val completionDate = LocalDate.now()
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              returnDetails = validSingleDisposalDesReturnDetails.returnDetails.copy(
                completionDate = completionDate
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.triageAnswers.completionDate) shouldBe Right(
            CompletionDate(completionDate)
          )
        }

      }

      "find the address correctly" in {
        mockGetTaxYearAndCalculatedTaxDue()

        val result = transformer.toCompleteReturn(validSingleDisposalDesReturnDetails)

        completeSingleDisposalReturnValue(result)(_.propertyAddress) shouldBe Right(
          ukAddress.copy(
            postcode = Postcode(ukAddress.postcode.stripAllSpaces)
          )
        )
      }

      "transform disposal details answers correctly" when {

        "finding the share of the property" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  percentOwned = BigDecimal("100")
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.disposalDetails.shareOfProperty) shouldBe Right(
            ShareOfProperty.Full
          )
        }

        "finding the disposal price" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  disposalPrice = BigDecimal("123.45")
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.disposalDetails.disposalPrice) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "finding the disposal fees" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  disposalFees = BigDecimal("123.45")
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.disposalDetails.disposalFees) shouldBe Right(
            AmountInPence(12345L)
          )
        }

      }

      "transform acquisition details answers correctly" when {

        "finding the acquisition method" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquisitionType = DesAcquisitionType.Inherited
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.acquisitionMethod) shouldBe Right(
            AcquisitionMethod.Inherited
          )
        }

        "finding the acquisition date" in {
          val date = LocalDate.now()
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquiredDate = date
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.acquisitionDate) shouldBe Right(
            AcquisitionDate(date)
          )
        }

        "finding the acquisition price" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquisitionPrice = BigDecimal("12345")
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.acquisitionPrice) shouldBe Right(
            AmountInPence(1234500L)
          )
        }

        "finding the rebased amount when one is defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  rebasedAmount = Some(BigDecimal("12345"))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.rebasedAcquisitionPrice) shouldBe Right(
            Some(AmountInPence(1234500L))
          )
        }

        "there is no rebased amount" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  rebasedAmount = None
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.rebasedAcquisitionPrice) shouldBe Right(None)
        }

        "the improvement costs are defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  improvementCosts = Some(BigDecimal("1.23"))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.improvementCosts) shouldBe Right(
            AmountInPence(123L)
          )
        }

        "the improvement costs are not defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  improvementCosts = None
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.improvementCosts) shouldBe Right(
            AmountInPence(0L)
          )
        }

        "finding the acquisition fees" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleDisposalDetails.copy(
                  acquisitionFees = BigDecimal("1.23")
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.acquisitionDetails.acquisitionFees) shouldBe Right(
            AmountInPence(123L)
          )
        }

      }

      "transform relief answers correctly" when {

        "the private residents relief is defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  privateResRelief = Some(BigDecimal("123.45"))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.privateResidentsRelief) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the private residents relief is not defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  privateResRelief = None
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.privateResidentsRelief) shouldBe Right(
            AmountInPence.zero
          )
        }

        "the letting relief is defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  lettingsReflief = Some(BigDecimal("123.45"))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.lettingsRelief) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the letting  relief is not defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  lettingsReflief = None
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.lettingsRelief) shouldBe Right(AmountInPence.zero)
        }

        "there is no other relief option defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  otherRelief = None,
                  otherReliefAmount = None
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.otherReliefs) shouldBe Right(None)
        }

        "the no other reliefs indicate the user selected no other reliefs" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  otherRelief = Some("none"),
                  otherReliefAmount = Some(BigDecimal(0))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.otherReliefs) shouldBe Right(
            Some(OtherReliefsOption.NoOtherReliefs)
          )
        }

        "the other reliefs answers indicate the user selected other reliefs" in {
          mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              reliefDetails = Some(
                validReliefDetails.copy(
                  otherRelief = Some("abc"),
                  otherReliefAmount = Some(BigDecimal("12.34"))
                )
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.reliefDetails.otherReliefs) shouldBe Right(
            Some(OtherReliefsOption.OtherReliefs("abc", AmountInPence(1234L)))
          )
        }

      }

      "transform exemption and losses answers correctly" when {

        "the in year losses is defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the in year losses is not defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = None
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(
            AmountInPence.zero
          )
        }

        "the previous year losses is defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.exemptionsAndLossesDetails.previousYearsLosses) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the previous year losses is not defined" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = None
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.exemptionsAndLossesDetails.previousYearsLosses) shouldBe Right(
            AmountInPence.zero
          )
        }

        "finding the annual exempt amount" in {
          mockGetTaxYearAndCalculatedTaxDue()

          val result = transformer.toCompleteReturn(
            validSingleDisposalDesReturnDetails.copy(
              incomeAllowanceDetails = validSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                annualExemption = BigDecimal("12.34")
              )
            )
          )

          completeSingleDisposalReturnValue(result)(_.exemptionsAndLossesDetails.annualExemptAmount) shouldBe Right(
            AmountInPence(1234L)
          )
        }

      }

      "transform year to date liability answers correctly" when {

        "the user has not selected other reliefs and" when {

          "the estimated income is defined" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(
              validSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  estimatedIncome = Some(BigDecimal("123.45"))
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.map(_.estimatedIncome)
            ) shouldBe Right(
              Right(AmountInPence(12345L))
            )
          }

          "the estimated income is not defined" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(
              validSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  estimatedIncome = None
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.map(_.estimatedIncome)
            ) shouldBe Right(
              Right(AmountInPence.zero)
            )
          }

          "the personal allowance is defined" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(
              validSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  personalAllowance = Some(BigDecimal("123.45"))
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.map(_.personalAllowance)
            ) shouldBe Right(
              Right(Some(AmountInPence(12345L)))
            )
          }

          "the personal is not defined" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(
              validSingleDisposalDesReturnDetails.copy(
                incomeAllowanceDetails = validSingleDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                  personalAllowance = None
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.map(_.personalAllowance)
            ) shouldBe Right(
              Right(None)
            )
          }

          "finding whether any of the details were estimated" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(
              validSingleDisposalDesReturnDetails.copy(
                returnDetails = validSingleDisposalDesReturnDetails.returnDetails.copy(
                  estimate = true
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.map(_.hasEstimatedDetails)
            ) shouldBe Right(
              Right(true)
            )
          }

          "getting the calculated tax due" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(validSingleDisposalDesReturnDetails)

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.map(_.calculatedTaxDue)
            ) shouldBe Right(
              Right(calculatedTaxDue)
            )
          }

          "finding the tax due" in {
            mockGetTaxYearAndCalculatedTaxDue()

            val result = transformer.toCompleteReturn(
              validSingleDisposalDesReturnDetails.copy(
                returnDetails = validSingleDisposalDesReturnDetails.returnDetails.copy(
                  totalLiability = BigDecimal("12345.67")
                )
              )
            )

            completeSingleDisposalReturnValue(result)(_.yearToDateLiabilityAnswers.map(_.taxDue)) shouldBe Right(
              Right(AmountInPence(1234567L))
            )
          }

        }

        "the user has selected other reliefs and" when {

          val returnDetailsWithOtherReliefs = validSingleDisposalDesReturnDetails.copy(
            reliefDetails =
              Some(sample[ReliefDetails].copy(otherRelief = Some("other"), otherReliefAmount = Some(BigDecimal("100"))))
          )

          "finding the taxableGainOrLoss when a loss has been made" in {
            mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

            val result = transformer.toCompleteReturn(
              returnDetailsWithOtherReliefs.copy(
                returnDetails = returnDetailsWithOtherReliefs.returnDetails.copy(
                  totalNetLoss = Some(BigDecimal("1")),
                  totalTaxableGain = BigDecimal("0")
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.leftMap(_.taxableGainOrLoss)
            ) shouldBe Right(
              Left(AmountInPence(-100L))
            )
          }

          "finding the taxableGainOrLoss when a gain has been made" in {
            mockGetTaxYear(validSingleDisposalDetails.disposalDate)(Some(taxYear))

            val result = transformer.toCompleteReturn(
              returnDetailsWithOtherReliefs.copy(
                returnDetails = returnDetailsWithOtherReliefs.returnDetails.copy(
                  totalNetLoss = None,
                  totalTaxableGain = BigDecimal("2")
                )
              )
            )

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.leftMap(_.taxableGainOrLoss)
            ) shouldBe Right(
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

            completeSingleDisposalReturnValue(result)(
              _.yearToDateLiabilityAnswers.leftMap(_.hasEstimatedDetails)
            ) shouldBe Right(
              Left(true)
            )
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

            completeSingleDisposalReturnValue(result)(_.yearToDateLiabilityAnswers.leftMap(_.taxDue)) shouldBe Right(
              Left(AmountInPence(300L))
            )

          }

        }

      }

    }

    "passed details of a multiple disposal" must {

      val validMultipleDisposalsDesReturnDetails = sample[DesReturnDetails].copy(
        disposalDetails = List(validMultipleDisposalDetails),
        returnDetails = sample[ReturnDetails].copy(isUKResident = true, customerType = CustomerType.Individual)
      )

      def completeMultipleDisposalsReturnValue[A](
        result: Either[Error, DisplayReturn]
      )(value: CompleteMultipleDisposalsReturn => A): Either[String, A] =
        result match {
          case Left(e)                 => Left(s"Expected CompleteMultipleDisposalsReturn but got $e")
          case Right(d: DisplayReturn) =>
            d.completeReturn match {
              case m @ CompleteMultipleDisposalsReturn(_, _, _, _, _, _, _, _) => Right(value(m))
              case other                                                       => Left(s"Expected CompleteMultipleDisposalsReturn but got $other")
            }
          case Right(other)            => Left(s"Expected CompleteMultipleDisposalsReturn but got $other")
        }

      def mockGetTaxYearSuccess(): Unit =
        inSequence {
          mockGetTaxYear(validMultipleDisposalDetails.disposalDate)(Some(taxYear))
        }

      "return an error" when {

        "the address is a non uk address" in {
          mockGetTaxYearSuccess()

          val desReturn = validMultipleDisposalsDesReturnDetails.copy(
            disposalDetails = List(
              validMultipleDisposalDetails.copy(addressDetails = Address.toAddressDetails(sample[NonUkAddress]))
            )
          )

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "a tax year cannot be found for the disposal date" in {
          mockGetTaxYear(validMultipleDisposalDetails.disposalDate)(None)

          transformer.toCompleteReturn(validMultipleDisposalsDesReturnDetails).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident cannot be found" in {
          val desReturn = validMultipleDisposalsDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = None
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident is not recognised" in {
          val desReturn = validMultipleDisposalsDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = Some("????")
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the asset types are not recognised" in {
          val desReturn = validMultipleDisposalsDesReturnDetails.copy(
            disposalDetails = List(
              validMultipleDisposalDetails.copy(
                assetType = DesAssetTypeValue("dunno")
              )
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

      }

      "transform triage answers correctly" when {

        "there are no represented personal details" in {
          mockGetTaxYearSuccess()

          val result: Either[Error, DisplayReturn] = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              representedPersonDetails = None
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(
              IndividualUserType.Self
            )
          )
        }

        "there are represented personal details but no date of death" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = None
                )
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "there are represented personal details with a date of death" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = Some(LocalDate.of(2000, 1, 1))
                )
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "the user was a trust" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleDisposalsDesReturnDetails.returnDetails.copy(
                customerType = CustomerType.Trust
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(None)
        }

        "the user was a uk resident" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = true
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(Country.uk)
        }

        "the user was not a uk resident and there is a valid country code" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = false,
                countryResidence = Some("HK")
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country("HK")
          )
        }

        "finding the asset types" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              disposalDetails = List(
                validMultipleDisposalDetails.copy(
                  assetType = DesAssetTypeValue("nonres res shares mix")
                )
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.assetTypes) shouldBe Right(
            List(AssetType.MixedUse, AssetType.IndirectDisposal, AssetType.Residential, AssetType.NonResidential)
          )
        }

        "finding the completion data" in {
          val completionDate = LocalDate.now()
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleDisposalsDesReturnDetails.returnDetails.copy(
                completionDate = completionDate
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.triageAnswers.completionDate) shouldBe Right(
            CompletionDate(completionDate)
          )
        }

      }

      "transform example property details answers correctly" when {

        "finding the address " in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validMultipleDisposalsDesReturnDetails)

          completeMultipleDisposalsReturnValue(result)(_.examplePropertyDetailsAnswers.address) shouldBe Right(
            ukAddress.copy(
              postcode = Postcode(ukAddress.postcode.stripAllSpaces)
            )
          )
        }

        "a tax year can be found for the disposal date" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validMultipleDisposalsDesReturnDetails)

          completeMultipleDisposalsReturnValue(result)(_.examplePropertyDetailsAnswers.disposalDate) shouldBe Right(
            DisposalDate(validMultipleDisposalDetails.disposalDate, taxYear)
          )
        }

        "finding the disposal price" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              disposalDetails = List(
                validMultipleDisposalDetails.copy(
                  disposalPrice = BigDecimal("123.45")
                )
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.examplePropertyDetailsAnswers.disposalPrice) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "finding the acquisition price" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              disposalDetails = List(
                validMultipleDisposalDetails.copy(
                  acquisitionPrice = BigDecimal("12345")
                )
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.examplePropertyDetailsAnswers.acquisitionPrice) shouldBe Right(
            AmountInPence(1234500L)
          )
        }

      }

      "transform exemption and losses answers correctly" when {

        "the in year losses is defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleDisposalsDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.exemptionAndLossesAnswers.inYearLosses) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the in year losses is not defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleDisposalsDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = None
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.exemptionAndLossesAnswers.inYearLosses) shouldBe Right(
            AmountInPence.zero
          )
        }

        "the previous year losses is defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleDisposalsDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.exemptionAndLossesAnswers.previousYearsLosses) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the previous year losses is not defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleDisposalsDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = None
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.exemptionAndLossesAnswers.previousYearsLosses) shouldBe Right(
            AmountInPence.zero
          )
        }

        "finding the annual exempt amount" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              incomeAllowanceDetails = validMultipleDisposalsDesReturnDetails.incomeAllowanceDetails.copy(
                annualExemption = BigDecimal("12.34")
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.exemptionAndLossesAnswers.annualExemptAmount) shouldBe Right(
            AmountInPence(1234L)
          )
        }

      }

      "transform year to date liability answers correctly" when {

        "finding the taxableGainOrLoss when a loss has been made" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleDisposalsDesReturnDetails.returnDetails.copy(
                totalNetLoss = Some(BigDecimal("1")),
                totalTaxableGain = BigDecimal("0")
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.yearToDateLiabilityAnswers.taxableGainOrLoss) shouldBe Right(
            AmountInPence(-100L)
          )
        }

        "finding the taxableGainOrLoss when a gain has been made" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleDisposalsDesReturnDetails.returnDetails.copy(
                totalNetLoss = None,
                totalTaxableGain = BigDecimal("2")
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.yearToDateLiabilityAnswers.taxableGainOrLoss) shouldBe Right(
            AmountInPence(200L)
          )
        }

        "finding whether a user has estimated any details" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleDisposalsDesReturnDetails.returnDetails.copy(
                estimate = true
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.yearToDateLiabilityAnswers.hasEstimatedDetails) shouldBe Right(
            true
          )
        }

        "finding the tax due for the user" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleDisposalsDesReturnDetails.returnDetails.copy(
                totalLiability = BigDecimal("3")
              )
            )
          )

          completeMultipleDisposalsReturnValue(result)(_.yearToDateLiabilityAnswers.taxDue) shouldBe Right(
            AmountInPence(300L)
          )

        }

      }

    }

    "passed details of a single indirect disposal" must {

      val validSingleIndirectDisposalDesReturnDetails = sample[DesReturnDetails].copy(
        disposalDetails = List(validSingleIndirectDisposalDetails),
        returnDetails = sample[ReturnDetails].copy(isUKResident = true, customerType = CustomerType.Individual),
        reliefDetails = None
      )

      def completeSingleIndirectDisposalReturnValue[A](
        result: Either[Error, DisplayReturn]
      )(value: CompleteSingleIndirectDisposalReturn => A): Either[String, A] =
        result match {
          case Left(e)                 => Left(s"Expected CompleteSingleDisposalReturn but got $e")
          case Right(d: DisplayReturn) =>
            d.completeReturn match {
              case s @ CompleteSingleIndirectDisposalReturn(_, _, _, _, _, _, _, _, _, _) => Right(value(s))
              case other                                                                  => Left(s"Expected CompleteSingleDisposalReturn but got $other")
            }
          case Right(other)            => Left(s"Expected CompleteSingleDisposalReturn but got $other")
        }

      def mockGetValidTaxYear(taxYear: TaxYear = sample[TaxYear]) =
        mockGetTaxYear(validSingleIndirectDisposalDetails.disposalDate)(Some(taxYear))

      "return an error" when {

        "a tax year cannot be found for the disposal date" in {
          mockGetTaxYear(validSingleIndirectDisposalDetails.disposalDate)(None)

          transformer.toCompleteReturn(validSingleIndirectDisposalDesReturnDetails).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident cannot be found" in {
          val desReturn = validSingleIndirectDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = None
            )
          )

          mockGetValidTaxYear()
          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident is not recognised" in {
          val desReturn = validSingleIndirectDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = Some("????")
            )
          )

          mockGetValidTaxYear()
          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

      }

      "transform triage answers correctly" when {

        "there are no represented personal details" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              representedPersonDetails = None
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(
              IndividualUserType.Self
            )
          )
        }

        "there are represented personal details but no date of death" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = None
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "there are represented personal details with a date of death" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = Some(LocalDate.of(2000, 1, 1))
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "the user was a trust" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = validSingleIndirectDisposalDesReturnDetails.returnDetails.copy(
                customerType = CustomerType.Trust
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(None)
        }

        "the user was a uk resident" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = true
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country.uk
          )
        }

        "the user was not a uk resident and there is a valid country code" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = false,
                countryResidence = Some("HK")
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country("HK")
          )
        }

        "finding the disposal method" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  disposalType = DesDisposalType.Sold
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.disposalMethod) shouldBe Right(
            DisposalMethod.Sold
          )
        }

        "a tax year can be found for the disposal date" in {
          mockGetValidTaxYear(taxYear)

          val result = transformer.toCompleteReturn(validSingleIndirectDisposalDesReturnDetails)

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.disposalDate) shouldBe Right(
            DisposalDate(validSingleIndirectDisposalDetails.disposalDate, taxYear)
          )
        }

        "finding the completion data" in {
          val completionDate = LocalDate.now()
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = validSingleIndirectDisposalDesReturnDetails.returnDetails.copy(
                completionDate = completionDate
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.triageAnswers.completionDate) shouldBe Right(
            CompletionDate(completionDate)
          )
        }

      }

      "find the address correctly" when {

        "the address is a uk address" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(validSingleIndirectDisposalDesReturnDetails)

          completeSingleIndirectDisposalReturnValue(result)(_.companyAddress) shouldBe Right(
            ukAddress.copy(
              postcode = Postcode(ukAddress.postcode.stripAllSpaces)
            )
          )
        }

        "the address is a non-uk address" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  addressDetails = Address.toAddressDetails(nonUkAddress)
                )
              )
            )
          )

          val expectedPostcode = nonUkAddress.postcode.map(p => p.copy(value = p.value.toUpperCase()))

          completeSingleIndirectDisposalReturnValue(result)(_.companyAddress) shouldBe Right(
            nonUkAddress.copy(postcode = expectedPostcode)
          )
        }

      }

      "transform disposal details answers correctly" when {

        "finding the share of the property" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  percentOwned = BigDecimal("100")
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.disposalDetails.shareOfProperty) shouldBe Right(
            ShareOfProperty.Full
          )
        }

        "finding the disposal price" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  disposalPrice = BigDecimal("123.45")
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.disposalDetails.disposalPrice) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "finding the disposal fees" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  disposalFees = BigDecimal("123.45")
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.disposalDetails.disposalFees) shouldBe Right(
            AmountInPence(12345L)
          )
        }

      }

      "transform acquisition details answers correctly" when {

        "finding the acquisition method" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  acquisitionType = DesAcquisitionType.Inherited
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.acquisitionDetails.acquisitionMethod) shouldBe Right(
            AcquisitionMethod.Inherited
          )
        }

        "finding the acquisition date" in {
          val date = LocalDate.now()
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  acquiredDate = date
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.acquisitionDetails.acquisitionDate) shouldBe Right(
            AcquisitionDate(date)
          )
        }

        "finding the acquisition price" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  acquisitionPrice = BigDecimal("12345")
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.acquisitionDetails.acquisitionPrice) shouldBe Right(
            AmountInPence(1234500L)
          )
        }

        "finding the rebased amount when one is defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  rebasedAmount = Some(BigDecimal("12345"))
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.acquisitionDetails.rebasedAcquisitionPrice
          ) shouldBe Right(
            Some(AmountInPence(1234500L))
          )
        }

        "there is no rebased amount" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  rebasedAmount = None
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.acquisitionDetails.rebasedAcquisitionPrice
          ) shouldBe Right(None)
        }

        "the improvement costs are defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  improvementCosts = Some(BigDecimal("1.23"))
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.acquisitionDetails.improvementCosts) shouldBe Right(
            AmountInPence(123L)
          )
        }

        "the improvement costs are not defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  improvementCosts = None
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.acquisitionDetails.improvementCosts) shouldBe Right(
            AmountInPence(0L)
          )
        }

        "finding the acquisition fees" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleIndirectDisposalDetails.copy(
                  acquisitionFees = BigDecimal("1.23")
                )
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.acquisitionDetails.acquisitionFees) shouldBe Right(
            AmountInPence(123L)
          )
        }

      }

      "transform exemption and losses answers correctly" when {

        "the in year losses is defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleIndirectDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the in year losses is not defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleIndirectDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = None
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(
            AmountInPence.zero
          )
        }

        "the previous year losses is defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleIndirectDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.exemptionsAndLossesDetails.previousYearsLosses
          ) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the previous year losses is not defined" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleIndirectDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = None
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.exemptionsAndLossesDetails.previousYearsLosses
          ) shouldBe Right(
            AmountInPence.zero
          )
        }

        "finding the annual exempt amount" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              incomeAllowanceDetails = validSingleIndirectDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                annualExemption = BigDecimal("12.34")
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.exemptionsAndLossesDetails.annualExemptAmount
          ) shouldBe Right(
            AmountInPence(1234L)
          )
        }

      }

      "transform year to date liability answers correctly" when {

        "finding the taxableGainOrLoss when a loss has been made" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = validSingleIndirectDisposalDesReturnDetails.returnDetails.copy(
                totalNetLoss = Some(BigDecimal("1")),
                totalTaxableGain = BigDecimal("0")
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxableGainOrLoss
          ) shouldBe Right(
            AmountInPence(-100L)
          )
        }

        "finding the taxableGainOrLoss when a gain has been made" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = validSingleIndirectDisposalDesReturnDetails.returnDetails.copy(
                totalNetLoss = None,
                totalTaxableGain = BigDecimal("2")
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxableGainOrLoss
          ) shouldBe Right(
            AmountInPence(200L)
          )
        }

        "finding whether a user has estimated any details" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = validSingleIndirectDisposalDesReturnDetails.returnDetails.copy(
                estimate = true
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.yearToDateLiabilityAnswers.hasEstimatedDetails
          ) shouldBe Right(
            true
          )
        }

        "finding the tax due for the user" in {
          mockGetValidTaxYear()

          val result = transformer.toCompleteReturn(
            validSingleIndirectDisposalDesReturnDetails.copy(
              returnDetails = validSingleIndirectDisposalDesReturnDetails.returnDetails.copy(
                totalLiability = BigDecimal("3")
              )
            )
          )

          completeSingleIndirectDisposalReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxDue
          ) shouldBe Right(
            AmountInPence(300L)
          )

        }

      }

    }

    "passed details of a multiple indirect disposal" must {

      val validMultipleIndirectDisposalsDesReturnDetails = sample[DesReturnDetails].copy(
        disposalDetails = List(validMultipleIndirectDisposalDetails),
        returnDetails = sample[ReturnDetails].copy(isUKResident = true, customerType = CustomerType.Individual)
      )

      def completeMultipleIndirectDisposalsReturnValue[A](
        result: Either[Error, DisplayReturn]
      )(value: CompleteMultipleIndirectDisposalReturn => A): Either[String, A] =
        result match {
          case Left(e)                 => Left(s"Expected CompleteMultipleIndirectDisposalReturn but got $e")
          case Right(m: DisplayReturn) =>
            m.completeReturn match {
              case m @ CompleteMultipleIndirectDisposalReturn(_, _, _, _, _, _, _, _) => Right(value(m))
              case other                                                              => Left(s"Expected CompleteMultipleIndirectDisposalReturn but got $other")
            }
          case Right(other)            => Left(s"Expected CompleteMultipleIndirectDisposalReturn but got $other")
        }

      def mockGetTaxYearSuccess(): Unit =
        inSequence {
          mockGetTaxYear(validMultipleIndirectDisposalDetails.disposalDate)(Some(taxYear))
        }

      "return an error" when {

        "a tax year cannot be found for the disposal date" in {
          mockGetTaxYear(validMultipleIndirectDisposalDetails.disposalDate)(None)

          transformer.toCompleteReturn(validMultipleIndirectDisposalsDesReturnDetails).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident cannot be found" in {
          val desReturn = validMultipleIndirectDisposalsDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = None
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident is not recognised" in {
          val desReturn = validMultipleIndirectDisposalsDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = Some("????")
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

      }

      "transform triage answers correctly" when {

        "there are no represented personal details" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              representedPersonDetails = None
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(
              IndividualUserType.Self
            )
          )
        }

        "there are represented personal details but no date of death" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = None
                )
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "there are represented personal details with a date of death" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = Some(LocalDate.of(2000, 1, 1))
                )
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "the user was a trust" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleIndirectDisposalsDesReturnDetails.returnDetails.copy(
                customerType = CustomerType.Trust
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(None)
        }

        "the user was a uk resident" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = true
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country.uk
          )
        }

        "the user was not a uk resident and there is a valid country code" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = false,
                countryResidence = Some("HK")
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country("HK")
          )
        }

        "finding the asset types" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validMultipleIndirectDisposalsDesReturnDetails)

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.assetTypes) shouldBe Right(
            List(AssetType.IndirectDisposal)
          )
        }

        "finding the completion data" in {
          val completionDate = LocalDate.now()
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleIndirectDisposalsDesReturnDetails.returnDetails.copy(
                completionDate = completionDate
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.triageAnswers.completionDate) shouldBe Right(
            CompletionDate(completionDate)
          )
        }

      }

      "transform example property details answers correctly" when {

        "finding the address " in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validMultipleIndirectDisposalsDesReturnDetails)

          completeMultipleIndirectDisposalsReturnValue(result)(_.exampleCompanyDetailsAnswers.address) shouldBe Right(
            ukAddress.copy(
              postcode = Postcode(ukAddress.postcode.stripAllSpaces)
            )
          )
        }

        "finding the disposal price" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              disposalDetails = List(
                validMultipleIndirectDisposalDetails.copy(
                  disposalPrice = BigDecimal("123.45")
                )
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exampleCompanyDetailsAnswers.disposalPrice
          ) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "finding the acquisition price" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              disposalDetails = List(
                validMultipleIndirectDisposalDetails.copy(
                  acquisitionPrice = BigDecimal("12345")
                )
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exampleCompanyDetailsAnswers.acquisitionPrice
          ) shouldBe Right(
            AmountInPence(1234500L)
          )
        }

      }

      "transform exemption and losses answers correctly" when {

        "the in year losses is defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleIndirectDisposalsDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.inYearLosses
          ) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the in year losses is not defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleIndirectDisposalsDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = None
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.inYearLosses
          ) shouldBe Right(
            AmountInPence.zero
          )
        }

        "the previous year losses is defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleIndirectDisposalsDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.previousYearsLosses
          ) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the previous year losses is not defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              lossSummaryDetails = validMultipleIndirectDisposalsDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = None
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.previousYearsLosses
          ) shouldBe Right(
            AmountInPence.zero
          )
        }

        "finding the annual exempt amount" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              incomeAllowanceDetails = validMultipleIndirectDisposalsDesReturnDetails.incomeAllowanceDetails.copy(
                annualExemption = BigDecimal("12.34")
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.annualExemptAmount
          ) shouldBe Right(
            AmountInPence(1234L)
          )
        }

      }

      "transform year to date liability answers correctly" when {

        "finding the taxableGainOrLoss when a loss has been made" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleIndirectDisposalsDesReturnDetails.returnDetails.copy(
                totalNetLoss = Some(BigDecimal("1")),
                totalTaxableGain = BigDecimal("0")
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxableGainOrLoss
          ) shouldBe Right(
            AmountInPence(-100L)
          )
        }

        "finding the taxableGainOrLoss when a gain has been made" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleIndirectDisposalsDesReturnDetails.returnDetails.copy(
                totalNetLoss = None,
                totalTaxableGain = BigDecimal("2")
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxableGainOrLoss
          ) shouldBe Right(
            AmountInPence(200L)
          )
        }

        "finding whether a user has estimated any details" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleIndirectDisposalsDesReturnDetails.returnDetails.copy(
                estimate = true
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(
            _.yearToDateLiabilityAnswers.hasEstimatedDetails
          ) shouldBe Right(
            true
          )
        }

        "finding the tax due for the user" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validMultipleIndirectDisposalsDesReturnDetails.copy(
              returnDetails = validMultipleIndirectDisposalsDesReturnDetails.returnDetails.copy(
                totalLiability = BigDecimal("3")
              )
            )
          )

          completeMultipleIndirectDisposalsReturnValue(result)(_.yearToDateLiabilityAnswers.taxDue) shouldBe Right(
            AmountInPence(300L)
          )

        }

      }

    }

    "passed details of a single mixed use disposal" must {

      val validSingleMixedUseDisposalDesReturnDetails = sample[DesReturnDetails].copy(
        disposalDetails = List(validSingleMixedUseDisposalDetails),
        returnDetails = sample[ReturnDetails].copy(isUKResident = true, customerType = CustomerType.Individual)
      )

      def completeSingleMixedUseDisposalsReturnValue[A](
        result: Either[Error, DisplayReturn]
      )(value: CompleteSingleMixedUseDisposalReturn => A): Either[String, A] =
        result match {
          case Left(e)                 => Left(s"Expected CompleteMultipleDisposalsReturn but got $e")
          case Right(d: DisplayReturn) =>
            d.completeReturn match {
              case m @ CompleteSingleMixedUseDisposalReturn(_, _, _, _, _, _, _, _) => Right(value(m))
              case other                                                            => Left(s"Expected CompleteSingleMixedUseDisposalReturn but got $other")
            }
          case Right(other)            => Left(s"Expected CompleteSingleMixedUseDisposalReturn but got $other")
        }

      def mockGetTaxYearSuccess(): Unit =
        inSequence {
          mockGetTaxYear(validSingleMixedUseDisposalDetails.disposalDate)(Some(taxYear))
        }

      "return an error" when {

        "the address is a non uk address" in {
          mockGetTaxYearSuccess()

          val desReturn = validSingleMixedUseDisposalDesReturnDetails.copy(
            disposalDetails = List(
              validSingleMixedUseDisposalDetails.copy(addressDetails = Address.toAddressDetails(sample[NonUkAddress]))
            )
          )

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "a tax year cannot be found for the disposal date" in {
          mockGetTaxYear(validSingleMixedUseDisposalDetails.disposalDate)(None)

          transformer.toCompleteReturn(validSingleMixedUseDisposalDesReturnDetails).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident cannot be found" in {
          val desReturn = validSingleMixedUseDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = None
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

        "the country of residence for a non-uk resident is not recognised" in {
          val desReturn = validSingleMixedUseDisposalDesReturnDetails.copy(
            returnDetails = sample[ReturnDetails].copy(
              isUKResident = false,
              countryResidence = Some("????")
            )
          )

          mockGetTaxYearSuccess()

          transformer.toCompleteReturn(desReturn).isLeft shouldBe true
        }

      }

      "transform triage answers correctly" when {

        "there are no represented personal details" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              representedPersonDetails = None
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(
              IndividualUserType.Self
            )
          )
        }

        "there are represented personal details but no date of death" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = None
                )
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "there are represented personal details with a date of death" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              representedPersonDetails = Some(
                sample[RepresentedPersonDetails].copy(
                  dateOfDeath = Some(LocalDate.of(2000, 1, 1))
                )
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(
            Some(IndividualUserType.Self)
          )
        }

        "the user was a trust" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = validSingleMixedUseDisposalDesReturnDetails.returnDetails.copy(
                customerType = CustomerType.Trust
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.individualUserType) shouldBe Right(None)
        }

        "the user was a uk resident" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = true
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country.uk
          )
        }

        "the user was not a uk resident and there is a valid country code" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = sample[ReturnDetails].copy(
                isUKResident = false,
                countryResidence = Some("HK")
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.countryOfResidence) shouldBe Right(
            Country("HK")
          )
        }

        "finding the disposal method" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleMixedUseDisposalDetails.copy(
                  disposalType = DesDisposalType.Sold
                )
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.disposalMethod) shouldBe Right(
            DisposalMethod.Sold
          )
        }

        "finding the asset type" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validSingleMixedUseDisposalDesReturnDetails)

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.assetType) shouldBe Right(
            AssetType.MixedUse
          )
        }

        "a tax year can be found for the disposal date" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validSingleMixedUseDisposalDesReturnDetails)

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.disposalDate) shouldBe Right(
            DisposalDate(validSingleMixedUseDisposalDetails.disposalDate, taxYear)
          )
        }

        "finding the completion data" in {
          val completionDate = LocalDate.now()
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = validSingleMixedUseDisposalDesReturnDetails.returnDetails.copy(
                completionDate = completionDate
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.triageAnswers.completionDate) shouldBe Right(
            CompletionDate(completionDate)
          )
        }

      }

      "transform example property details answers correctly" when {

        "finding the address " in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(validSingleMixedUseDisposalDesReturnDetails)

          completeSingleMixedUseDisposalsReturnValue(result)(_.propertyDetailsAnswers.address) shouldBe Right(
            ukAddress.copy(
              postcode = Postcode(ukAddress.postcode.stripAllSpaces)
            )
          )
        }

        "finding the disposal price" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleMixedUseDisposalDetails.copy(
                  disposalPrice = BigDecimal("123.45")
                )
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.propertyDetailsAnswers.disposalPrice) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "finding the acquisition price" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              disposalDetails = List(
                validSingleMixedUseDisposalDetails.copy(
                  acquisitionPrice = BigDecimal("12345")
                )
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.propertyDetailsAnswers.acquisitionPrice) shouldBe Right(
            AmountInPence(1234500L)
          )
        }

      }

      "transform exemption and losses answers correctly" when {

        "the in year losses is defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleMixedUseDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the in year losses is not defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleMixedUseDisposalDesReturnDetails.lossSummaryDetails.copy(
                inYearLossUsed = None
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.exemptionsAndLossesDetails.inYearLosses) shouldBe Right(
            AmountInPence.zero
          )
        }

        "the previous year losses is defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleMixedUseDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = Some(BigDecimal("123.45"))
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.previousYearsLosses
          ) shouldBe Right(
            AmountInPence(12345L)
          )
        }

        "the previous year losses is not defined" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              lossSummaryDetails = validSingleMixedUseDisposalDesReturnDetails.lossSummaryDetails.copy(
                preYearLossUsed = None
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.previousYearsLosses
          ) shouldBe Right(
            AmountInPence.zero
          )
        }

        "finding the annual exempt amount" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              incomeAllowanceDetails = validSingleMixedUseDisposalDesReturnDetails.incomeAllowanceDetails.copy(
                annualExemption = BigDecimal("12.34")
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(
            _.exemptionsAndLossesDetails.annualExemptAmount
          ) shouldBe Right(
            AmountInPence(1234L)
          )
        }

      }

      "transform year to date liability answers correctly" when {

        "finding the taxableGainOrLoss when a loss has been made" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = validSingleMixedUseDisposalDesReturnDetails.returnDetails.copy(
                totalNetLoss = Some(BigDecimal("1")),
                totalTaxableGain = BigDecimal("0")
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxableGainOrLoss
          ) shouldBe Right(
            AmountInPence(-100L)
          )
        }

        "finding the taxableGainOrLoss when a gain has been made" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = validSingleMixedUseDisposalDesReturnDetails.returnDetails.copy(
                totalNetLoss = None,
                totalTaxableGain = BigDecimal("2")
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(
            _.yearToDateLiabilityAnswers.taxableGainOrLoss
          ) shouldBe Right(
            AmountInPence(200L)
          )
        }

        "finding whether a user has estimated any details" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = validSingleMixedUseDisposalDesReturnDetails.returnDetails.copy(
                estimate = true
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(
            _.yearToDateLiabilityAnswers.hasEstimatedDetails
          ) shouldBe Right(
            true
          )
        }

        "finding the tax due for the user" in {
          mockGetTaxYearSuccess()

          val result = transformer.toCompleteReturn(
            validSingleMixedUseDisposalDesReturnDetails.copy(
              returnDetails = validSingleMixedUseDisposalDesReturnDetails.returnDetails.copy(
                totalLiability = BigDecimal("3")
              )
            )
          )

          completeSingleMixedUseDisposalsReturnValue(result)(_.yearToDateLiabilityAnswers.taxDue) shouldBe Right(
            AmountInPence(300L)
          )

        }

      }

    }

    "passed details of more than one disposal" must {

      "return an error" in {
        val result = transformer.toCompleteReturn(
          sample[DesReturnDetails].copy(
            disposalDetails = List(validSingleDisposalDetails, validMultipleDisposalDetails)
          )
        )

        result.isLeft shouldBe true

      }

    }

  }

}
