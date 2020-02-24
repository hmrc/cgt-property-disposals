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
import uk.gov.hmrc.cgtpropertydisposals.models.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.ReturnDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.GainCalculatedTaxDue
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.NumberOfProperties.One
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CompleteYearToDateLiabilityAnswers

class ReturnDetailsSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  "ReturnDetails getTaxableGainOrNetLoss" must {

    "return totalTaxableGain value when exemptionsAndLossesDetails.taxableGainOrLoss exist" in {
      val amountInPounds = BigDecimal(1000)
      val completeReturn = sample[CompleteReturn].copy(
        triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(numberOfProperties = One),
        exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers]
          .copy(taxableGainOrLoss = Some(AmountInPence.fromPounds(amountInPounds))),
        yearToDateLiabilityAnswers = sample[CompleteYearToDateLiabilityAnswers]
      )
      val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

      ReturnDetails(submitReturnRequest).totalTaxableGain shouldBe amountInPounds
    }

    "return totalTaxableGain value when exemptionsAndLossesDetails.taxableGainOrLoss doesn't exist" in {
      val amountInPounds = BigDecimal(1000)
      val calculatedTaxDue =
        sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
      val completeReturn = sample[CompleteReturn].copy(
        triageAnswers              = sample[CompleteSingleDisposalTriageAnswers].copy(numberOfProperties = One),
        exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers].copy(taxableGainOrLoss    = None),
        yearToDateLiabilityAnswers = sample[CompleteYearToDateLiabilityAnswers]
          .copy(hasEstimatedDetailsWithCalculatedTaxDue =
            sample[HasEstimatedDetailsWithCalculatedTaxDue].copy(calculatedTaxDue = calculatedTaxDue)
          )
      )
      val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

      ReturnDetails(submitReturnRequest).totalTaxableGain shouldBe amountInPounds
    }

    "return some value for totalNetLoss" in {
      val amountInPounds = BigDecimal(1000)
      val calculatedTaxDue =
        sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
      val completeReturn = sample[CompleteReturn].copy(
        triageAnswers              = sample[CompleteSingleDisposalTriageAnswers].copy(numberOfProperties = One),
        exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers].copy(taxableGainOrLoss    = None),
        yearToDateLiabilityAnswers = sample[CompleteYearToDateLiabilityAnswers]
          .copy(hasEstimatedDetailsWithCalculatedTaxDue =
            sample[HasEstimatedDetailsWithCalculatedTaxDue].copy(calculatedTaxDue = calculatedTaxDue)
          )
      )
      val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

      ReturnDetails.apply(submitReturnRequest).totalNetLoss.isDefined shouldBe false
    }

    "return none for totalNetLoss" in {
      val amountInPounds = BigDecimal(-1000)
      val calculatedTaxDue =
        sample[GainCalculatedTaxDue].copy(taxableGainOrNetLoss = AmountInPence.fromPounds(amountInPounds))
      val completeReturn = sample[CompleteReturn].copy(
        triageAnswers              = sample[CompleteSingleDisposalTriageAnswers].copy(numberOfProperties = One),
        exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers].copy(taxableGainOrLoss    = None),
        yearToDateLiabilityAnswers = sample[CompleteYearToDateLiabilityAnswers]
          .copy(hasEstimatedDetailsWithCalculatedTaxDue =
            sample[HasEstimatedDetailsWithCalculatedTaxDue].copy(calculatedTaxDue = calculatedTaxDue)
          )
      )
      val submitReturnRequest = sample[SubmitReturnRequest].copy(completeReturn = completeReturn)

      ReturnDetails.apply(submitReturnRequest).totalNetLoss shouldBe Some(amountInPounds)
    }

  }
}
