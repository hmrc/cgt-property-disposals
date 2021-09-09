/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.GainCalculatedTaxDue
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, SubmitReturnRequest, TaxableAmountOfMoney}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

class ReturnDetailsSpec extends AnyWordSpec with Matchers with MockFactory with ScalaCheckDrivenPropertyChecks {

  "ReturnDetails" when {

    "getting the TaxableGainOrNetLoss" when {

      "given a calculated journey" must {

        "return totalTaxableGain value when the user has made a gain" in {
          val amountInPounds      = BigDecimal(1000)
          val calculatedTaxDue    =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
          val completeReturn      = sample[CompleteSingleDisposalReturn].copy(
            yearToDateLiabilityAnswers = Right(
              sample[CompleteCalculatedYTDAnswers]
                .copy(calculatedTaxDue = calculatedTaxDue)
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          val result = ReturnDetails(submitReturnRequest)
          result.totalTaxableGain shouldBe amountInPounds
          result.totalNetLoss     shouldBe None
        }

        "return totalTaxableGain value when the user has made a net loss" in {
          val calculatedTaxDue    =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.zero)
          val completeReturn      = sample[CompleteSingleDisposalReturn].copy(
            yearToDateLiabilityAnswers = Right(
              sample[CompleteCalculatedYTDAnswers]
                .copy(calculatedTaxDue = calculatedTaxDue)
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          val result = ReturnDetails(submitReturnRequest)
          result.totalTaxableGain shouldBe BigDecimal("0")
          result.totalNetLoss     shouldBe None
        }

        "return some value for totalNetLoss when a loss has been made" in {
          val amountInPounds      = BigDecimal(-1000)
          val calculatedTaxDue    =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
          val completeReturn      = sample[CompleteSingleDisposalReturn].copy(
            yearToDateLiabilityAnswers = Right(
              sample[CompleteCalculatedYTDAnswers]
                .copy(calculatedTaxDue = calculatedTaxDue)
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          val result = ReturnDetails(submitReturnRequest)
          result.totalTaxableGain shouldBe BigDecimal("0")
          result.totalNetLoss     shouldBe Some(amountInPounds * -1)
        }

      }

      "given a non-calculated journey" which {

        def commonBehaviour(toCompleteReturn: CompleteNonCalculatedYTDAnswers => CompleteReturn): Unit = {
          "return totalTaxableGain value when the user has made a gain" in {
            val amountInPounds      = BigDecimal(1000)
            val completeReturn      = toCompleteReturn(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.fromPounds(amountInPounds))
            )
            val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

            val result = ReturnDetails(submitReturnRequest)
            result.totalTaxableGain shouldBe amountInPounds
            result.totalNetLoss     shouldBe None
          }

          "return totalTaxableGain value when the user has made a net loss" in {
            val completeReturn      = toCompleteReturn(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.zero)
            )
            val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

            val result = ReturnDetails(submitReturnRequest)
            result.totalTaxableGain shouldBe BigDecimal("0")
            result.totalNetLoss     shouldBe None
          }

          "return some value for totalNetLoss when a loss has been made" in {
            val amountInPounds      = BigDecimal(-1000)
            val completeReturn      = toCompleteReturn(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.fromPounds(amountInPounds))
            )
            val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

            val result = ReturnDetails(submitReturnRequest)
            result.totalTaxableGain shouldBe BigDecimal("0")
            result.totalNetLoss     shouldBe Some(amountInPounds * -1)
          }

        }

        "is on a single disposal journey" must {
          behave like commonBehaviour(ytdAnswers =>
            sample[CompleteSingleDisposalReturn].copy(
              yearToDateLiabilityAnswers = Left(ytdAnswers)
            )
          )
        }

        "is on a multiple disposals journey" must {
          behave like commonBehaviour(ytdAnswers =>
            sample[CompleteMultipleDisposalsReturn].copy(
              yearToDateLiabilityAnswers = ytdAnswers
            )
          )
        }

      }

    }

    "given a single disposal return" must {

      val completeReturn = sample[CompleteSingleDisposalReturn].copy(hasAttachments = false)

      val singleDisposalSubmitReturnRequest = sample[SubmitReturnRequest].copy(
        completeReturn = completeReturn
      )

      "find the correct customer type" in {

        ReturnDetails(
          singleDisposalSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Left(sample[TrustName])
            )
          )
        ).customerType shouldBe CustomerType.Trust

        ReturnDetails(
          singleDisposalSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Right(sample[IndividualName])
            )
          )
        ).customerType shouldBe CustomerType.Individual
      }

      "find the correct completion date" in {
        ReturnDetails(
          singleDisposalSubmitReturnRequest
        ).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest =
          singleDisposalSubmitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              triageAnswers = completeReturn.triageAnswers.copy(
                countryOfResidence = country
              )
            )
          )

        val nonUkCountry = Country("HK")
        val ukResult     = ReturnDetails(requestWithCountry(Country.uk))
        val nonUkResult  = ReturnDetails(requestWithCountry(nonUkCountry))

        ukResult.isUKResident    shouldBe true
        nonUkResult.isUKResident shouldBe false

        ukResult.countryResidence    shouldBe None
        nonUkResult.countryResidence shouldBe Some(nonUkCountry.code)
      }

      "populate the correct number of properties" in {
        ReturnDetails(singleDisposalSubmitReturnRequest).numberDisposals shouldBe 1
      }

      "not populate the value at band details when there aren't any" in {
        ReturnDetails(
          singleDisposalSubmitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              yearToDateLiabilityAnswers = Left(sample[CompleteNonCalculatedYTDAnswers])
            )
          )
        ).valueAtTaxBandDetails shouldBe None
      }

      "find the value at band details when the return is a calculated one" in {
        val calculatedYearToDateAnswers = sample[CompleteCalculatedYTDAnswers].copy(
          calculatedTaxDue = sample[GainCalculatedTaxDue].copy(
            taxDueAtLowerRate = TaxableAmountOfMoney(BigDecimal("1"), AmountInPence(1L)),
            taxDueAtHigherRate = TaxableAmountOfMoney(BigDecimal("2"), AmountInPence(2L))
          )
        )
        ReturnDetails(
          singleDisposalSubmitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              yearToDateLiabilityAnswers = Right(calculatedYearToDateAnswers)
            )
          )
        ).valueAtTaxBandDetails shouldBe Some(
          List(
            ValueAtTaxBandDetails(BigDecimal("1"), BigDecimal("0.01")),
            ValueAtTaxBandDetails(BigDecimal("2"), BigDecimal("0.02"))
          )
        )
      }

      "find the correct total liability, year to date value and estimate flag for a calculated journey" in {
        forAll { calculatedYtdAnswers: CompleteCalculatedYTDAnswers =>
          val result = ReturnDetails(
            singleDisposalSubmitReturnRequest.copy(
              completeReturn = completeReturn.copy(
                yearToDateLiabilityAnswers = Right(calculatedYtdAnswers)
              )
            )
          )

          result.totalLiability    shouldBe calculatedYtdAnswers.taxDue.inPounds()
          result.totalYTDLiability shouldBe calculatedYtdAnswers.taxDue.inPounds()
          result.estimate          shouldBe calculatedYtdAnswers.hasEstimatedDetails
        }
      }

      "find the correct total liability and year to date value and estimate flag for a non-calculated journey" in {
        forAll { nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
          val result = ReturnDetails(
            singleDisposalSubmitReturnRequest.copy(
              completeReturn = completeReturn.copy(
                yearToDateLiabilityAnswers = Left(nonCalculatedYtdAnswers)
              )
            )
          )

          result.totalLiability    shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.yearToDateLiability
            .getOrElse(nonCalculatedYtdAnswers.taxDue)
            .inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }

      "set the repayment flag to false for a calculated return" in {
        forAll { ytdAnswers: CompleteCalculatedYTDAnswers =>
          val completeReturn      = sample[CompleteSingleDisposalReturn]
            .copy(yearToDateLiabilityAnswers = Right(ytdAnswers))
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          ReturnDetails(submitReturnRequest).repayment shouldBe false
        }
      }

      "set the repayment flag correctly for a non-calculated return" in {
        forAll { ytdAnswers: CompleteNonCalculatedYTDAnswers =>
          val completeReturn      = sample[CompleteSingleDisposalReturn]
            .copy(yearToDateLiabilityAnswers = Left(ytdAnswers))
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          ReturnDetails(submitReturnRequest).repayment shouldBe ytdAnswers.checkForRepayment.getOrElse(false)
        }
      }

      "set the underived fields properly" in {
        val result = ReturnDetails(singleDisposalSubmitReturnRequest)

        result.attachmentUpload    shouldBe false
        result.declaration         shouldBe true
        result.adjustedAmount      shouldBe None
        result.attachmentID        shouldBe None
        result.entrepreneursRelief shouldBe None
      }

    }

    "given a multiple disposals return" must {

      val completeReturn = sample[CompleteMultipleDisposalsReturn].copy(hasAttachments = true)

      val multipleDisposalsSubmitReturnRequest = sample[SubmitReturnRequest].copy(
        completeReturn = completeReturn
      )

      "find the correct customer type" in {

        ReturnDetails(
          multipleDisposalsSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Left(sample[TrustName])
            )
          )
        ).customerType shouldBe CustomerType.Trust

        ReturnDetails(
          multipleDisposalsSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Right(sample[IndividualName])
            )
          )
        ).customerType shouldBe CustomerType.Individual
      }

      "find the correct completion date" in {
        ReturnDetails(
          multipleDisposalsSubmitReturnRequest
        ).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest =
          multipleDisposalsSubmitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              triageAnswers = completeReturn.triageAnswers.copy(
                countryOfResidence = country
              )
            )
          )

        val nonUkCountry = Country("HK")
        val ukResult     = ReturnDetails(requestWithCountry(Country.uk))
        val nonUkResult  = ReturnDetails(requestWithCountry(nonUkCountry))

        ukResult.isUKResident    shouldBe true
        nonUkResult.isUKResident shouldBe false

        ukResult.countryResidence    shouldBe None
        nonUkResult.countryResidence shouldBe Some(nonUkCountry.code)
      }

      "populate the correct number of properties" in {
        ReturnDetails(
          multipleDisposalsSubmitReturnRequest
        ).numberDisposals shouldBe completeReturn.triageAnswers.numberOfProperties
      }

      "find the correct total liability and year to date value and estimate flag" in {
        forAll { nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
          val result = ReturnDetails(
            multipleDisposalsSubmitReturnRequest.copy(
              completeReturn = completeReturn.copy(
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
            )
          )

          result.totalLiability    shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.yearToDateLiability
            .getOrElse(nonCalculatedYtdAnswers.taxDue)
            .inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }

      "set the repayment flag correctly" in {
        forAll { ytdAnswers: CompleteNonCalculatedYTDAnswers =>
          val completeReturn      = sample[CompleteSingleDisposalReturn]
            .copy(yearToDateLiabilityAnswers = Left(ytdAnswers))
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          ReturnDetails(submitReturnRequest).repayment shouldBe ytdAnswers.checkForRepayment.getOrElse(false)
        }
      }

      "set the underived fields properly" in {
        val result = ReturnDetails(multipleDisposalsSubmitReturnRequest)

        result.valueAtTaxBandDetails shouldBe None
        result.attachmentUpload      shouldBe true
        result.declaration           shouldBe true
        result.adjustedAmount        shouldBe None
        result.attachmentID          shouldBe None
        result.entrepreneursRelief   shouldBe None
      }

    }

    "given a single indirect disposal return" must {

      val completeReturn = sample[CompleteSingleIndirectDisposalReturn].copy(hasAttachments = true)

      val singleIndirectDisposalSubmitReturnRequest = sample[SubmitReturnRequest].copy(
        completeReturn = completeReturn
      )

      "find the correct customer type" in {

        ReturnDetails(
          singleIndirectDisposalSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Left(sample[TrustName])
            )
          )
        ).customerType shouldBe CustomerType.Trust

        ReturnDetails(
          singleIndirectDisposalSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Right(sample[IndividualName])
            )
          )
        ).customerType shouldBe CustomerType.Individual
      }

      "find the correct completion date" in {
        ReturnDetails(
          singleIndirectDisposalSubmitReturnRequest
        ).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest =
          singleIndirectDisposalSubmitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              triageAnswers = completeReturn.triageAnswers.copy(
                countryOfResidence = country
              )
            )
          )

        val nonUkCountry = Country("HK")
        val ukResult     = ReturnDetails(requestWithCountry(Country.uk))
        val nonUkResult  = ReturnDetails(requestWithCountry(nonUkCountry))

        ukResult.isUKResident    shouldBe true
        nonUkResult.isUKResident shouldBe false

        ukResult.countryResidence    shouldBe None
        nonUkResult.countryResidence shouldBe Some(nonUkCountry.code)
      }

      "populate the correct number of properties" in {
        ReturnDetails(singleIndirectDisposalSubmitReturnRequest).numberDisposals shouldBe 1
      }

      "not populate the value at band details" in {
        ReturnDetails(singleIndirectDisposalSubmitReturnRequest).valueAtTaxBandDetails shouldBe None
      }

      "find the correct total liability and year to date value and estimate flag for a non-calculated journey" in {
        forAll { nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
          val result = ReturnDetails(
            singleIndirectDisposalSubmitReturnRequest.copy(
              completeReturn = completeReturn.copy(
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
            )
          )

          result.totalLiability    shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.yearToDateLiability
            .getOrElse(nonCalculatedYtdAnswers.taxDue)
            .inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }

      "set the repayment flag correctly" in {
        forAll { ytdAnswers: CompleteNonCalculatedYTDAnswers =>
          val completeReturn      = sample[CompleteSingleDisposalReturn]
            .copy(yearToDateLiabilityAnswers = Left(ytdAnswers))
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          ReturnDetails(submitReturnRequest).repayment shouldBe ytdAnswers.checkForRepayment.getOrElse(false)
        }
      }

      "set the underived fields properly" in {
        val result = ReturnDetails(singleIndirectDisposalSubmitReturnRequest)

        result.attachmentUpload    shouldBe true
        result.declaration         shouldBe true
        result.adjustedAmount      shouldBe None
        result.attachmentID        shouldBe None
        result.entrepreneursRelief shouldBe None
      }

    }

    "given a multiple indirect disposals return" must {

      val completeReturn = sample[CompleteMultipleIndirectDisposalReturn].copy(hasAttachments = true)

      val multipleIndirectDisposalsSubmitReturnRequest = sample[SubmitReturnRequest].copy(
        completeReturn = completeReturn
      )

      "find the correct customer type" in {

        ReturnDetails(
          multipleIndirectDisposalsSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Left(sample[TrustName])
            )
          )
        ).customerType shouldBe CustomerType.Trust

        ReturnDetails(
          multipleIndirectDisposalsSubmitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Right(sample[IndividualName])
            )
          )
        ).customerType shouldBe CustomerType.Individual
      }

      "find the correct completion date" in {
        ReturnDetails(
          multipleIndirectDisposalsSubmitReturnRequest
        ).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest =
          multipleIndirectDisposalsSubmitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              triageAnswers = completeReturn.triageAnswers.copy(
                countryOfResidence = country
              )
            )
          )

        val nonUkCountry = Country("HK")
        val ukResult     = ReturnDetails(requestWithCountry(Country.uk))
        val nonUkResult  = ReturnDetails(requestWithCountry(nonUkCountry))

        ukResult.isUKResident    shouldBe true
        nonUkResult.isUKResident shouldBe false

        ukResult.countryResidence    shouldBe None
        nonUkResult.countryResidence shouldBe Some(nonUkCountry.code)
      }

      "populate the correct number of properties" in {
        ReturnDetails(
          multipleIndirectDisposalsSubmitReturnRequest
        ).numberDisposals shouldBe completeReturn.triageAnswers.numberOfProperties
      }

      "find the correct total liability and year to date value and estimate flag" in {
        forAll { nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
          val result = ReturnDetails(
            multipleIndirectDisposalsSubmitReturnRequest.copy(
              completeReturn = completeReturn.copy(
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
            )
          )

          result.totalLiability    shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.yearToDateLiability
            .getOrElse(nonCalculatedYtdAnswers.taxDue)
            .inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }

      "set the repayment flag correctly" in {
        forAll { ytdAnswers: CompleteNonCalculatedYTDAnswers =>
          val completeReturn      = sample[CompleteSingleDisposalReturn]
            .copy(yearToDateLiabilityAnswers = Left(ytdAnswers))
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          ReturnDetails(submitReturnRequest).repayment shouldBe ytdAnswers.checkForRepayment.getOrElse(false)
        }
      }

      "set the underived fields properly" in {
        val result = ReturnDetails(multipleIndirectDisposalsSubmitReturnRequest)

        result.valueAtTaxBandDetails shouldBe None
        result.attachmentUpload      shouldBe true
        result.declaration           shouldBe true
        result.adjustedAmount        shouldBe None
        result.attachmentID          shouldBe None
        result.entrepreneursRelief   shouldBe None
      }

    }

    "given a single mixed use disposal return" must {

      val completeReturn = sample[CompleteSingleMixedUseDisposalReturn].copy(hasAttachments = true)

      val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

      "find the correct customer type" in {

        ReturnDetails(
          submitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Left(sample[TrustName])
            )
          )
        ).customerType shouldBe CustomerType.Trust

        ReturnDetails(
          submitReturnRequest.copy(
            subscribedDetails = sample[SubscribedDetails].copy(
              name = Right(sample[IndividualName])
            )
          )
        ).customerType shouldBe CustomerType.Individual
      }

      "find the correct completion date" in {
        ReturnDetails(
          submitReturnRequest
        ).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest =
          submitReturnRequest.copy(
            completeReturn = completeReturn.copy(
              triageAnswers = completeReturn.triageAnswers.copy(
                countryOfResidence = country
              )
            )
          )

        val nonUkCountry = Country("HK")
        val ukResult     = ReturnDetails(requestWithCountry(Country.uk))
        val nonUkResult  = ReturnDetails(requestWithCountry(nonUkCountry))

        ukResult.isUKResident    shouldBe true
        nonUkResult.isUKResident shouldBe false

        ukResult.countryResidence    shouldBe None
        nonUkResult.countryResidence shouldBe Some(nonUkCountry.code)
      }

      "populate the correct number of properties" in {
        ReturnDetails(
          submitReturnRequest
        ).numberDisposals shouldBe 1
      }

      "find the correct total liability and year to date value and estimate flag" in {
        forAll { nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
          val result = ReturnDetails(
            submitReturnRequest.copy(
              completeReturn = completeReturn.copy(
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
            )
          )

          result.totalLiability    shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.yearToDateLiability
            .getOrElse(nonCalculatedYtdAnswers.taxDue)
            .inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }

      "set the repayment flag correctly" in {
        forAll { ytdAnswers: CompleteNonCalculatedYTDAnswers =>
          val completeReturn      = sample[CompleteSingleDisposalReturn]
            .copy(yearToDateLiabilityAnswers = Left(ytdAnswers))
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          ReturnDetails(submitReturnRequest).repayment shouldBe ytdAnswers.checkForRepayment.getOrElse(false)
        }
      }

      "set the underived fields properly" in {
        val result = ReturnDetails(submitReturnRequest)

        result.valueAtTaxBandDetails shouldBe None
        result.attachmentUpload      shouldBe true
        result.declaration           shouldBe true
        result.adjustedAmount        shouldBe None
        result.attachmentID          shouldBe None
        result.entrepreneursRelief   shouldBe None
      }

    }

  }

}
