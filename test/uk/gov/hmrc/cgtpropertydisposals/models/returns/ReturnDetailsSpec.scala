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

import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.ReturnDetails
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.GainCalculatedTaxDue
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

class ReturnDetailsSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  "ReturnDetails" when {

    "getting the TaxableGainOrNetLoss" when {

      "given a calculated journey" must {

        "return totalTaxableGain value when the user has made a gain" in {
          val amountInPounds = BigDecimal(1000)
          val calculatedTaxDue =
            sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            triageAnswers              = sample[CompleteSingleDisposalTriageAnswers],
            exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers],
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
            triageAnswers              = sample[CompleteSingleDisposalTriageAnswers],
            exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers],
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
            triageAnswers              = sample[CompleteSingleDisposalTriageAnswers],
            exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers],
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

      "given a non-calculated journey" must {

        "return totalTaxableGain value when the user has made a gain" in {
          val amountInPounds = BigDecimal(1000)
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            triageAnswers              = sample[CompleteSingleDisposalTriageAnswers],
            exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers],
            yearToDateLiabilityAnswers = Left(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.fromPounds(amountInPounds))
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          val result = ReturnDetails(submitReturnRequest)
          result.totalTaxableGain shouldBe amountInPounds
          result.totalNetLoss     shouldBe None
        }

        "return totalTaxableGain value when the user has made a net loss" in {
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            triageAnswers              = sample[CompleteSingleDisposalTriageAnswers],
            exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers],
            yearToDateLiabilityAnswers = Left(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.zero)
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          val result = ReturnDetails(submitReturnRequest)
          result.totalTaxableGain shouldBe BigDecimal("0")
          result.totalNetLoss     shouldBe None
        }

        "return some value for totalNetLoss when a loss has been made" in {
          val amountInPounds = BigDecimal(-1000)
          val completeReturn = sample[CompleteSingleDisposalReturn].copy(
            triageAnswers              = sample[CompleteSingleDisposalTriageAnswers],
            exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers],
            yearToDateLiabilityAnswers = Left(
              sample[CompleteNonCalculatedYTDAnswers]
                .copy(taxableGainOrLoss = AmountInPence.fromPounds(amountInPounds))
            )
          )
          val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

          val result = ReturnDetails(submitReturnRequest)
          result.totalTaxableGain shouldBe BigDecimal("0")
          result.totalNetLoss     shouldBe Some(amountInPounds * -1)
        }

      }

    }
  }

}
