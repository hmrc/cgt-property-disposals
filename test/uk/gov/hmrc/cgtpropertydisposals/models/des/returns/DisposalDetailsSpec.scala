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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.address.Postcode
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails, SingleMixedUseDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmountInPenceWithSource, Source}

class DisposalDetailsSpec extends WordSpec with Matchers with MockFactory with ScalaCheckDrivenPropertyChecks {

  "DisposalDetails" when {

    def singleDisposalDetailsValue[A](
      disposalDetails: DisposalDetails
    )(value: SingleDisposalDetails => A): Either[String, A] =
      disposalDetails match {
        case s: SingleDisposalDetails => Right(value(s))
        case other                    => Left(s"Expected single disposals details but got $other")
      }

    def multipleDisposalsDetailsValue[A](
      disposalDetails: DisposalDetails
    )(value: MultipleDisposalDetails => A): Either[String, A] =
      disposalDetails match {
        case s: MultipleDisposalDetails => Right(value(s))
        case other                      =>
          Left(s"Expected multiple disposals details but got $other")
      }

    def singleMixedUseDisposalDetailsValue[A](
      disposalDetails: DisposalDetails
    )(value: SingleMixedUseDisposalDetails => A): Either[String, A] =
      disposalDetails match {
        case s: SingleMixedUseDisposalDetails => Right(value(s))
        case other                            => Left(s"Expected single disposals details but got $other")
      }

    "given a single disposal return" must {

      "populate the initial gain or loss correctly" when {

        "return no values for either initialGain or initialLoss when calculated" in {
          val calculatedTaxDue = sample[GainCalculatedTaxDue]
            .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(123456), Source.Calculated))

          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = None,
            yearToDateLiabilityAnswers =
              Right(sample[CompleteCalculatedYTDAnswers].copy(calculatedTaxDue = calculatedTaxDue))
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
        }

        "return some value as initialGain and none as initialLoss" in {
          val calculatedTaxDue = sample[GainCalculatedTaxDue]
            .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(123456), Source.UserSupplied))

          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = Some(AmountInPence(123456)),
            yearToDateLiabilityAnswers =
              Right(sample[CompleteCalculatedYTDAnswers].copy(calculatedTaxDue = calculatedTaxDue))
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("1234.56")))
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
        }

        "return none as initialGain and some value for initialLoss" in {
          val calculatedTaxDue = sample[NonGainCalculatedTaxDue]
            .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(-123456), Source.UserSupplied))

          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = Some(AmountInPence(-123456)),
            yearToDateLiabilityAnswers = Right(
              sample[CompleteCalculatedYTDAnswers]
                .copy(calculatedTaxDue = calculatedTaxDue)
            )
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(Some(BigDecimal("1234.56")))
        }
      }

      "populate the improvement costs correctly" when {

        "the improvementCosts are non zero" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(acquisitionDetails =
            sample[CompleteAcquisitionDetailsAnswers]
              .copy(improvementCosts = AmountInPence(1234))
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.improvements)     shouldBe Right(true)
          singleDisposalDetailsValue(result)(_.improvementCosts) shouldBe Right(Some(BigDecimal("12.34")))
        }

        "the  improvementCosts are zero" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(acquisitionDetails =
            sample[CompleteAcquisitionDetailsAnswers]
              .copy(improvementCosts = AmountInPence.zero)
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.improvements)     shouldBe Right(false)
          singleDisposalDetailsValue(result)(_.improvementCosts) shouldBe Right(None)
        }
      }

      "populate the disposal date correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          completeReturn.copy(propertyAddress =
            sample[UkAddress].copy(
              postcode = Postcode("TZ1 1 T  Z")
            )
          )
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.propertyAddress))
        }
      }

      "populate the asset type correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "populate the acquisition price correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.acquisitionDetails.acquisitionPrice.inPounds())
        }
      }

      "populate the rebased acquisition price fields correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          val result             = DisposalDetails(completeReturn)
          val acquisitionDetails = completeReturn.acquisitionDetails

          singleDisposalDetailsValue(result)(_.rebased)       shouldBe Right(acquisitionDetails.shouldUseRebase)
          singleDisposalDetailsValue(result)(_.rebasedAmount) shouldBe Right(
            acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds())
          )

        }
      }

      "populate the disposal price correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalPrice) shouldBe Right(
            completeReturn.disposalDetails.disposalPrice.inPounds()
          )
        }
      }

      "populate the percent owned field correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.percentOwned) shouldBe Right(
            completeReturn.disposalDetails.shareOfProperty.percentageValue
          )
        }
      }

      "populate the acquisition date field correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquiredDate) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionDate.value
          )
        }
      }

      "populate the disposal type field correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalType) shouldBe Right(
            DesDisposalType(completeReturn.triageAnswers.disposalMethod)
          )
        }
      }

      "populate the acquisition fees field correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquisitionFees) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionFees.inPounds()
          )
        }
      }

      "populate the disposal fees field correctly" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalFees) shouldBe Right(
            completeReturn.disposalDetails.disposalFees.inPounds()
          )
        }
      }

      "set the land registry flag to false" in {
        forAll { completeReturn: CompleteSingleDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

    }

    "given a multiple disposals return" must {

      "populate the disposal date correctly" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.examplePropertyDetailsAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.examplePropertyDetailsAnswers.address))
        }
      }

      "populate the asset types correctly" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "set the acquisition type to a dummy value" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionType
          ) shouldBe Right(DesAcquisitionType.Other("not captured for multiple disposals"))
        }
      }

      "set the land registry flag to false" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

      "populate the rebased acquisition price field correctly" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.examplePropertyDetailsAnswers.acquisitionPrice.inPounds())
        }
      }

      "populate the disposal price correctly" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalPrice
          ) shouldBe Right(completeReturn.examplePropertyDetailsAnswers.disposalPrice.inPounds())
        }
      }

      "set the rebased flag to false" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.rebased
          ) shouldBe Right(false)
        }
      }

      "set the improvements flag to false" in {
        forAll { completeReturn: CompleteMultipleDisposalsReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.improvements
          ) shouldBe Right(false)
        }
      }

    }

    "given a single indirect disposal return" must {

      "populate the initial gain or loss correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
        }
      }

      "populate the improvement costs correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.improvements)     shouldBe Right(false)
          singleDisposalDetailsValue(result)(_.improvementCosts) shouldBe Right(None)
        }
      }

      "populate the disposal date correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.companyAddress))
        }
      }

      "populate the asset type correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "populate the acquisition price correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.acquisitionDetails.acquisitionPrice.inPounds())
        }
      }

      "populate the rebased acquisition price fields correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          val result             = DisposalDetails(completeReturn)
          val acquisitionDetails = completeReturn.acquisitionDetails

          singleDisposalDetailsValue(result)(_.rebased)       shouldBe Right(acquisitionDetails.shouldUseRebase)
          singleDisposalDetailsValue(result)(_.rebasedAmount) shouldBe Right(
            acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds())
          )

        }
      }

      "populate the disposal price correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalPrice) shouldBe Right(
            completeReturn.disposalDetails.disposalPrice.inPounds()
          )
        }
      }

      "populate the percent owned field correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.percentOwned) shouldBe Right(
            completeReturn.disposalDetails.shareOfProperty.percentageValue
          )
        }
      }

      "populate the acquisition date field correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquiredDate) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionDate.value
          )
        }
      }

      "populate the disposal type field correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalType) shouldBe Right(
            DesDisposalType(completeReturn.triageAnswers.disposalMethod)
          )
        }
      }

      "populate the acquisition fees field correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquisitionFees) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionFees.inPounds()
          )
        }
      }

      "populate the disposal fees field correctly" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalFees) shouldBe Right(
            completeReturn.disposalDetails.disposalFees.inPounds()
          )
        }
      }

      "set the land registry flag to false" in {
        forAll { completeReturn: CompleteSingleIndirectDisposalReturn =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

    }

    "given a multiple indirect disposals return" must {

      "populate the disposal date correctly" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.completionDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.exampleCompanyDetailsAnswers.address))
        }
      }

      "populate the asset types correctly" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "set the acquisition type to a dummy value" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionType
          ) shouldBe Right(DesAcquisitionType.Other("not captured for multiple disposals"))
        }
      }

      "set the land registry flag to false" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

      "populate the acquisition price field correctly" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.exampleCompanyDetailsAnswers.acquisitionPrice.inPounds())
        }
      }

      "populate the disposal price correctly" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalPrice
          ) shouldBe Right(completeReturn.exampleCompanyDetailsAnswers.disposalPrice.inPounds())
        }
      }

      "set the rebased flag to false" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.rebased
          ) shouldBe Right(false)
        }
      }

      "set the improvements flag to false" in {
        forAll { completeReturn: CompleteMultipleIndirectDisposalReturn =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.improvements
          ) shouldBe Right(false)
        }
      }

    }

    "given a single mixed use disposals return" must {

      "populate the disposal date correctly" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.propertyDetailsAnswers.address))
        }
      }

      "populate the asset types correctly" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "populate the disposal type field correctly" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalType) shouldBe Right(
            DesDisposalType(completeReturn.triageAnswers.disposalMethod)
          )
        }
      }

      "set the acquisition type to a dummy value" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionType
          ) shouldBe Right(DesAcquisitionType.Other("not captured for single mixed use disposals"))
        }
      }

      "set the land registry flag to false" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

      "populate the rebased acquisition price field correctly" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.propertyDetailsAnswers.acquisitionPrice.inPounds())
        }
      }

      "populate the disposal price correctly" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.disposalPrice
          ) shouldBe Right(completeReturn.propertyDetailsAnswers.disposalPrice.inPounds())
        }
      }

      "set the rebased flag to false" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.rebased
          ) shouldBe Right(false)
        }
      }

      "set the improvements flag to false" in {
        forAll { completeReturn: CompleteSingleMixedUseDisposalReturn =>
          singleMixedUseDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.improvements
          ) shouldBe Right(false)
        }
      }

    }

  }

}
