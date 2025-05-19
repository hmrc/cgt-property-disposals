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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn, CompleteSingleMixedUseDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.NonCalculatedYTDAnswers.CompleteNonCalculatedYTDAnswers

class IncomeAllowanceDetailsSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "IncomeAllowanceDetails" must {
    "have an apply method" which {
      "creates IncomeAllowanceDetails correctly" when {
        "the return passed in is for a single disposal calculated journey" in {
          forAll {
            calculatedYtdAnswers: CompleteCalculatedYTDAnswers =>
              val calculatedReturn = sample[CompleteSingleDisposalReturn].copy(
                exemptionsAndLossesDetails =
                  sample[CompleteExemptionAndLossesAnswers].copy(annualExemptAmount = AmountInPence(1L)),
                yearToDateLiabilityAnswers = Right(calculatedYtdAnswers)
              )
              IncomeAllowanceDetails(calculatedReturn) shouldBe IncomeAllowanceDetails(
                BigDecimal("0.01"),
                Some(calculatedYtdAnswers.estimatedIncome.inPounds()),
                calculatedYtdAnswers.personalAllowance.map(_.inPounds()),
                Some(calculatedReturn.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
              )
          }
        }

        "the return passed in is for a single disposal non-calculated journey" in {
          forAll {
            nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
              val calculatedReturn = sample[CompleteSingleDisposalReturn].copy(
                exemptionsAndLossesDetails =
                  sample[CompleteExemptionAndLossesAnswers].copy(annualExemptAmount = AmountInPence(1L)),
                yearToDateLiabilityAnswers = Left(nonCalculatedYtdAnswers)
              )
              IncomeAllowanceDetails(calculatedReturn) shouldBe IncomeAllowanceDetails(
                BigDecimal("0.01"),
                nonCalculatedYtdAnswers.estimatedIncome.map(_.inPounds()),
                nonCalculatedYtdAnswers.personalAllowance.map(_.inPounds()),
                Some(calculatedReturn.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
              )
          }
        }

        "the return passed in is for a multiple disposals journey" in {
          forAll {
            nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
              val calculatedReturn = sample[CompleteMultipleDisposalsReturn].copy(
                exemptionAndLossesAnswers =
                  sample[CompleteExemptionAndLossesAnswers].copy(annualExemptAmount = AmountInPence(1L)),
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
              IncomeAllowanceDetails(calculatedReturn) shouldBe IncomeAllowanceDetails(
                BigDecimal("0.01"),
                nonCalculatedYtdAnswers.estimatedIncome.map(_.inPounds()),
                nonCalculatedYtdAnswers.personalAllowance.map(_.inPounds()),
                Some(calculatedReturn.triageAnswers.taxYear.incomeTaxHigherRateThreshold.inPounds())
              )
          }
        }

        "the return passed in is for a single indirect disposal journey" in {
          forAll {
            nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
              val calculatedReturn = sample[CompleteSingleIndirectDisposalReturn].copy(
                exemptionsAndLossesDetails =
                  sample[CompleteExemptionAndLossesAnswers].copy(annualExemptAmount = AmountInPence(1L)),
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
              IncomeAllowanceDetails(calculatedReturn) shouldBe IncomeAllowanceDetails(
                BigDecimal("0.01"),
                nonCalculatedYtdAnswers.estimatedIncome.map(_.inPounds()),
                nonCalculatedYtdAnswers.personalAllowance.map(_.inPounds()),
                Some(calculatedReturn.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
              )
          }
        }

        "the return passed in is for a multiple indirect disposals journey" in {
          forAll {
            nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
              val calculatedReturn = sample[CompleteMultipleIndirectDisposalReturn].copy(
                exemptionsAndLossesDetails =
                  sample[CompleteExemptionAndLossesAnswers].copy(annualExemptAmount = AmountInPence(1L)),
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
              IncomeAllowanceDetails(calculatedReturn) shouldBe IncomeAllowanceDetails(
                BigDecimal("0.01"),
                nonCalculatedYtdAnswers.estimatedIncome.map(_.inPounds()),
                nonCalculatedYtdAnswers.personalAllowance.map(_.inPounds()),
                Some(calculatedReturn.triageAnswers.taxYear.incomeTaxHigherRateThreshold.inPounds())
              )
          }
        }

        "the return passed in is for a single mixed use disposal journey" in {
          forAll {
            nonCalculatedYtdAnswers: CompleteNonCalculatedYTDAnswers =>
              val completeReturn = sample[CompleteSingleMixedUseDisposalReturn].copy(
                exemptionsAndLossesDetails =
                  sample[CompleteExemptionAndLossesAnswers].copy(annualExemptAmount = AmountInPence(1L)),
                yearToDateLiabilityAnswers = nonCalculatedYtdAnswers
              )
              IncomeAllowanceDetails(completeReturn) shouldBe IncomeAllowanceDetails(
                BigDecimal("0.01"),
                nonCalculatedYtdAnswers.estimatedIncome.map(_.inPounds()),
                nonCalculatedYtdAnswers.personalAllowance.map(_.inPounds()),
                Some(completeReturn.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
              )
          }
        }
      }
    }
  }
}
