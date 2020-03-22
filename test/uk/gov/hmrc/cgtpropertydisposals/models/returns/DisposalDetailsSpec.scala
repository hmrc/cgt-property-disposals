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
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails.{MultipleDisposalDetails, SingleDisposalDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.{GainCalculatedTaxDue, NonGainCalculatedTaxDue}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CalculatedYTDAnswers.CompleteCalculatedYTDAnswers

class DisposalDetailsSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  def testSingleDisposalDetails(disposalDetails: DisposalDetails)(f: SingleDisposalDetails => Unit) =
    disposalDetails match {
      case s: SingleDisposalDetails => f(s)
      case m: MultipleDisposalDetails =>
        fail(s"Expected single disposal details but got multiple disposal details: $m")
    }

  "DisposalDetails getInitialGainOrLoss" must {

    "return no values for either initialGain or initialLoss when calculated" in {
      val calculatedTaxDue = sample[GainCalculatedTaxDue]
        .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(123456), Source.Calculated))

      val completeReturn = sample[CompleteReturn].copy(
        initialGainOrLoss          = None,
        yearToDateLiabilityAnswers = sample[CompleteCalculatedYTDAnswers].copy(calculatedTaxDue = calculatedTaxDue)
      )

      testSingleDisposalDetails(DisposalDetails(completeReturn)) { details =>
        details.initialGain shouldBe None
        details.initialLoss shouldBe None
      }
    }

    "return some value as initialGain and none as initialLoss" in {
      val calculatedTaxDue = sample[GainCalculatedTaxDue]
        .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(123456), Source.UserSupplied))

      val completeReturn = sample[CompleteReturn].copy(
        initialGainOrLoss          = Some(AmountInPence(123456)),
        yearToDateLiabilityAnswers = sample[CompleteCalculatedYTDAnswers].copy(calculatedTaxDue = calculatedTaxDue)
      )

      testSingleDisposalDetails(DisposalDetails(completeReturn)) { details =>
        details.initialGain shouldBe Some(BigDecimal("1234.56"))
        details.initialLoss shouldBe None
      }
    }

    "return none as initialGain and some value for initialLoss" in {
      val calculatedTaxDue = sample[NonGainCalculatedTaxDue]
        .copy(initialGainOrLoss = AmountInPenceWithSource(AmountInPence(-123456), Source.UserSupplied))

      val completeReturn = sample[CompleteReturn].copy(
        initialGainOrLoss = Some(AmountInPence(-123456)),
        yearToDateLiabilityAnswers = sample[CompleteCalculatedYTDAnswers]
          .copy(calculatedTaxDue = calculatedTaxDue)
      )

      testSingleDisposalDetails(DisposalDetails(completeReturn)) { details =>
        details.initialLoss shouldBe Some(BigDecimal("1234.56"))
        details.initialGain shouldBe None
      }
    }
  }

  "DisposalDetails improvementCosts" must {
    "return some value as improvementCosts" in {
      val completeReturn = sample[CompleteReturn].copy(acquisitionDetails = sample[CompleteAcquisitionDetailsAnswers]
        .copy(improvementCosts = AmountInPence(1234))
      )

      testSingleDisposalDetails(DisposalDetails(completeReturn)) { details =>
        details.improvementCosts shouldBe Some(BigDecimal("12.34"))
        details.improvements     shouldBe true
      }
    }

    "return none as improvementCosts" in {
      val completeReturn = sample[CompleteReturn].copy(acquisitionDetails = sample[CompleteAcquisitionDetailsAnswers]
        .copy(improvementCosts = AmountInPence(-1234))
      )

      testSingleDisposalDetails(DisposalDetails(completeReturn)) { details =>
        details.improvementCosts shouldBe None
        details.improvements     shouldBe false
      }
    }

  }

}
