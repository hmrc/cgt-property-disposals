/*
 * Copyright 2025 HM Revenue & Customs
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
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.generators.CompleteReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.LowerPriorityReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleIndirectDisposalReturn, CompleteSingleIndirectDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

class DisposalDetailsSplitSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

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

    "given a single indirect disposal return" must {
      "populate the improvement costs correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          val result = DisposalDetails(completeReturn)
          singleDisposalDetailsValue(result)(_.improvements)     shouldBe Right(false)
          singleDisposalDetailsValue(result)(_.improvementCosts) shouldBe Right(None)
        }
      }

      "populate the disposal date correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.disposalDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.companyAddress))
        }
      }

      "populate the asset type correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "populate the acquisition price correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.acquisitionDetails.acquisitionPrice.inPounds())
        }
      }

      "populate the rebased acquisition price fields correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          val result             = DisposalDetails(completeReturn)
          val acquisitionDetails = completeReturn.acquisitionDetails

          singleDisposalDetailsValue(result)(_.rebased)       shouldBe Right(acquisitionDetails.shouldUseRebase)
          singleDisposalDetailsValue(result)(_.rebasedAmount) shouldBe Right(
            acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds())
          )
        }
      }

      "populate the disposal price correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalPrice) shouldBe Right(
            completeReturn.disposalDetails.disposalPrice.inPounds()
          )
        }
      }

      "populate the percent owned field correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.percentOwned) shouldBe Right(
            completeReturn.disposalDetails.shareOfProperty.percentageValue
          )
        }
      }

      "populate the acquisition date field correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquiredDate) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionDate.value
          )
        }
      }

      "populate the disposal type field correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalType) shouldBe Right(
            DesDisposalType(completeReturn.triageAnswers.disposalMethod)
          )
        }
      }

      "populate the acquisition fees field correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.acquisitionFees) shouldBe Right(
            completeReturn.acquisitionDetails.acquisitionFees.inPounds()
          )
        }
      }

      "populate the disposal fees field correctly" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(_.disposalFees) shouldBe Right(
            completeReturn.disposalDetails.disposalFees.inPounds()
          )
        }
      }

      "set the land registry flag to false" in {
        forAll { (completeReturn: CompleteSingleIndirectDisposalReturn) =>
          singleDisposalDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

      "set the initial gain or loss when there is a gain after reliefs" in {
        val completeReturn = sample[CompleteSingleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(1L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0.01")))
        singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }

      "set the initial gain or loss when there is a loss after reliefs" in {
        val completeReturn = sample[CompleteSingleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(-1L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
        singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(Some(BigDecimal("0.01")))
      }

      "set the initial gain or loss when there is zero gain or loss after reliefs" in {
        val completeReturn = sample[CompleteSingleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(0L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0")))
        singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }

      "set the initial gain or loss when there is no gain or loss after reliefs given" in {
        val completeReturn = sample[CompleteSingleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = None,
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        singleDisposalDetailsValue(result)(_.initialGain) shouldBe Right(None)
        singleDisposalDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }
    }

    "given a multiple indirect disposals return" must {
      "populate the disposal date correctly" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalDate
          ) shouldBe Right(completeReturn.triageAnswers.completionDate.value)
        }
      }

      "populate the address correctly" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.addressDetails
          ) shouldBe Right(Address.toAddressDetails(completeReturn.exampleCompanyDetailsAnswers.address))
        }
      }

      "populate the asset types correctly" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.assetType
          ) shouldBe Right(DesAssetTypeValue(completeReturn))
        }
      }

      "set the acquisition type to a dummy value" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionType
          ) shouldBe Right(DesAcquisitionType.Other("not captured for multiple disposals"))
        }
      }

      "set the land registry flag to false" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.landRegistry
          ) shouldBe Right(false)
        }
      }

      "populate the acquisition price field correctly" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.acquisitionPrice
          ) shouldBe Right(completeReturn.exampleCompanyDetailsAnswers.acquisitionPrice.inPounds())
        }
      }

      "populate the disposal price correctly" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.disposalPrice
          ) shouldBe Right(completeReturn.exampleCompanyDetailsAnswers.disposalPrice.inPounds())
        }
      }

      "set the rebased flag to false" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.rebased
          ) shouldBe Right(false)
        }
      }

      "set the improvements flag to false" in {
        forAll { (completeReturn: CompleteMultipleIndirectDisposalReturn) =>
          multipleDisposalsDetailsValue(DisposalDetails(completeReturn))(
            _.improvements
          ) shouldBe Right(false)
        }
      }

      "set the initial gain or loss when there is a gain after reliefs" in {
        val completeReturn = sample[CompleteMultipleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(1L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0.01")))
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }

      "set the initial gain or loss when there is a loss after reliefs" in {
        val completeReturn = sample[CompleteMultipleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(-1L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(None)
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(Some(BigDecimal("0.01")))
      }

      "set the initial gain or loss when there is zero gain or loss after reliefs" in {
        val completeReturn = sample[CompleteMultipleIndirectDisposalReturn].copy(
          gainOrLossAfterReliefs = Some(AmountInPence(0L)),
          yearToDateLiabilityAnswers = sample[CompleteNonCalculatedYTDAnswers]
        )

        val result = DisposalDetails(completeReturn)
        multipleDisposalsDetailsValue(result)(_.initialGain) shouldBe Right(Some(BigDecimal("0")))
        multipleDisposalsDetailsValue(result)(_.initialLoss) shouldBe Right(None)
      }

      "set the initial gain or loss when there is no gain or loss after reliefs given" in {
        val completeReturn = sample[CompleteMultipleIndirectDisposalReturn].copy(
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
