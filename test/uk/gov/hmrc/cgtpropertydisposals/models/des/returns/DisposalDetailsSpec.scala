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

import io.github.martinhh.derived.arbitrary.anyGivenArbitrary
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.generators.AddressGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.LowerPriorityReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.ReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.CompleteReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteSingleDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AmountInPenceWithSource, Source}

class DisposalDetailsSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

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

    "given a single disposal return" must {
      "populate the initial gain or loss correctly" when {
        "return no values for either initialGain or initialLoss when calculated" in {
          val calculatedTaxDue = sample[GainCalculatedTaxDue]
            .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(123456), Source.Calculated))

          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = None,
            gainOrLossAfterReliefs = None,
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

        "there is no initial gain or loss for a non calculated return" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = None,
            gainOrLossAfterReliefs = None,
            yearToDateLiabilityAnswers = Left(sample[CompleteNonCalculatedYTDAnswers])
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
        }

        "there is no initial gain or loss but there is a gain after reliefs for a non calculated return" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = None,
            gainOrLossAfterReliefs = Some(AmountInPence(1L)),
            yearToDateLiabilityAnswers = Left(sample[CompleteNonCalculatedYTDAnswers])
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0.01")))
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
        }

        "there is no initial gain or loss but there is a loss after reliefs for a non calculated return" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = None,
            gainOrLossAfterReliefs = Some(AmountInPence(-1L)),
            yearToDateLiabilityAnswers = Left(sample[CompleteNonCalculatedYTDAnswers])
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(Some(BigDecimal("0.01")))
        }

        "there is no initial gain or loss but there is zero gain or loss after reliefs for a non calculated return" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            initialGainOrLoss = None,
            gainOrLossAfterReliefs = Some(AmountInPence(0L)),
            yearToDateLiabilityAnswers = Left(sample[CompleteNonCalculatedYTDAnswers])
          )

          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0")))
          singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
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

        "the improvementCosts are zero" in {
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
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
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
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "populate the acquisition price correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.acquisitionDetails.acquisitionPrice.inPounds())
        }
      }

      "populate the rebased acquisition price fields correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          val result             = DisposalDetails(completeReturn)
          val acquisitionDetails = completeReturn.acquisitionDetails

          singleDisposalDetailsValue(result)(_.rebased)       shouldBe Right(acquisitionDetails.shouldUseRebase)
          singleDisposalDetailsValue(result)(_.rebasedAmount) shouldBe Right(
            acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds())
          )
        }
      }

      "populate the disposal price correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalPrice) shouldBe Right(
            completeReturn.disposalDetails.disposalPrice.inPounds()
          )
        }
      }

      "populate the percent owned field correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.percentOwned) shouldBe Right(
            completeReturn.disposalDetails.shareOfProperty.percentageValue
          )
        }
      }

      "populate the acquisition date field correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquiredDate) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionDate.value
          )
        }
      }

      "populate the disposal type field correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalType) shouldBe Right(
            DesDisposalType(completeReturn.triageAnswers.disposalMethod)
          )
        }
      }

      "populate the acquisition fees field correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquisitionFees) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionFees.inPounds()
          )
        }
      }

      "populate the disposal fees field correctly" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalFees) shouldBe Right(
            completeReturn.disposalDetails.disposalFees.inPounds()
          )
        }
      }

      "set the land registry flag to false" in {
        forAll { (completeReturn: CompleteSingleDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }
    }

    "given a multiple disposals return" must {
      "populate the disposal date correctly" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.examplePropertyDetailsAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.examplePropertyDetailsAnswers.address))
        }
      }

      "populate the asset types correctly" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "set the acquisition type to a dummy value" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionType
          ) shouldBe Right(DesAcquisitionType.Other("not captured for multiple disposals"))
        }
      }

      "set the land registry flag to false" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

      "populate the rebased acquisition price field correctly" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.examplePropertyDetailsAnswers.acquisitionPrice.inPounds())
        }
      }

      "populate the disposal price correctly" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalPrice
          ) shouldBe Right(completeReturn.examplePropertyDetailsAnswers.disposalPrice.inPounds())
        }
      }

      "set the rebased flag to false" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.rebased
          ) shouldBe Right(false)
        }
      }

      "set the improvements flag to false" in {
        forAll { (completeReturn: CompleteMultipleDisposalsReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.improvements
          ) shouldBe Right(false)
        }
      }

      "set the initial gain or loss when there is a gain after reliefs" in {
        val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(1L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0.01")))
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }

      "set the initial gain or loss when there is a loss after reliefs" in {
        val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(-1L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(None)
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(Some(BigDecimal("0.01")))
      }

      "set the initial gain or loss when there is zero gain or loss after reliefs" in {
        val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(0L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0")))
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }

      "set the initial gain or loss when there is no gain or loss after reliefs given" in {
        val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(
          gainOrLossAfterReliefs = None,
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(None)
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }
    }
  }
}
