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
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.GainCalculatedTaxDue
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteSingleDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, SubmitReturnRequest, TaxableAmountOfMoney}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

class ReturnDetailsSpec extends WordSpec with Matchers with MockFactory with ScalaCheckDrivenPropertyChecks {

  "ReturnDetails" when {

    "getting the TaxableGainOrNetLoss" when {

      "given a calculated journey" must {

        "return totalTaxableGain value when the user has made a gain" in {
          val amountInPounds = BigDecimal(1000)
          val calculatedTaxDue =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
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
          val calculatedTaxDue =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.zero)
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
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
          val amountInPounds = BigDecimal(-1000)
          val calculatedTaxDue =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
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
            val amountInPounds = BigDecimal(1000)
            val completeReturn = toCompleteReturn(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.fromPounds(amountInPounds))
            )
            val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

            val result = ReturnDetails(submitReturnRequest)
            result.totalTaxableGain shouldBe amountInPounds
            result.totalNetLoss     shouldBe None
          }

          "return totalTaxableGain value when the user has made a net loss" in {
            val completeReturn = toCompleteReturn(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.zero)
            )
            val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

            val result = ReturnDetails(submitReturnRequest)
            result.totalTaxableGain shouldBe BigDecimal("0")
            result.totalNetLoss     shouldBe None
          }

          "return some value for totalNetLoss when a loss has been made" in {
            val amountInPounds = BigDecimal(-1000)
            val completeReturn = toCompleteReturn(
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

      val completeReturn = sample[CompleteSingleDisposalReturn]

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
        ReturnDetails(singleDisposalSubmitReturnRequest).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest = singleDisposalSubmitReturnRequest.copy(
          completeReturn = completeReturn.copy(
            triageAnswers = completeReturn.triageAnswers.copy(
              countryOfResidence = country
            )
          )
        )

        val nonUkCountry = Country("HK", Some("Hong Kong"))
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
            taxDueAtLowerRate  = TaxableAmountOfMoney(BigDecimal("1"), AmountInPence(1L)),
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
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }
      "set the underived fields properly" in {
        val result = ReturnDetails(singleDisposalSubmitReturnRequest)

        result.repayment           shouldBe false
        result.attachmentUpload    shouldBe false
        result.declaration         shouldBe true
        result.adjustedAmount      shouldBe None
        result.attachmentID        shouldBe None
        result.entrepreneursRelief shouldBe None
      }

    }

    "given a multiple disposals return" must {

      val completeReturn = sample[CompleteMultipleDisposalsReturn]

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
        ReturnDetails(multipleDisposalsSubmitReturnRequest).completionDate shouldBe completeReturn.triageAnswers.completionDate.value
      }

      "find the correct residency status and country" in {
        def requestWithCountry(country: Country): SubmitReturnRequest = multipleDisposalsSubmitReturnRequest.copy(
          completeReturn = completeReturn.copy(
            triageAnswers = completeReturn.triageAnswers.copy(
              countryOfResidence = country
            )
          )
        )

        val nonUkCountry = Country("HK", Some("Hong Kong"))
        val ukResult     = ReturnDetails(requestWithCountry(Country.uk))
        val nonUkResult  = ReturnDetails(requestWithCountry(nonUkCountry))

        ukResult.isUKResident    shouldBe true
        nonUkResult.isUKResident shouldBe false

        ukResult.countryResidence    shouldBe None
        nonUkResult.countryResidence shouldBe Some(nonUkCountry.code)
      }

      "populate the correct number of properties" in {
        ReturnDetails(multipleDisposalsSubmitReturnRequest).numberDisposals shouldBe completeReturn.triageAnswers.numberOfProperties
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
          result.totalYTDLiability shouldBe nonCalculatedYtdAnswers.taxDue.inPounds()
          result.estimate          shouldBe nonCalculatedYtdAnswers.hasEstimatedDetails

        }
      }
      "set the underived fields properly" in {
        val result = ReturnDetails(multipleDisposalsSubmitReturnRequest)

        result.valueAtTaxBandDetails shouldBe None
        result.repayment             shouldBe false
        result.attachmentUpload      shouldBe false
        result.declaration           shouldBe true
        result.adjustedAmount        shouldBe None
        result.attachmentID          shouldBe None
        result.entrepreneursRelief   shouldBe None
      }

    }

  }

}
