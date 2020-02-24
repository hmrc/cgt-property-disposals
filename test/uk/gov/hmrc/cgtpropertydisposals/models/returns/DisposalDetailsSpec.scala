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
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DisposalDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue.GainCalculatedTaxDue
import uk.gov.hmrc.cgtpropertydisposals.models.returns.YearToDateLiabilityAnswers.CompleteYearToDateLiabilityAnswers

class DisposalDetailsSpec extends WordSpec  with Matchers with MockFactory with HttpSupport {

  "DisposalDetails getInitialGainOrLoss" must {

    "return some value as initialGain and none as initialLoss" in {
      val calculatedTaxDue = sample[GainCalculatedTaxDue].copy(initialGainOrLoss = AmountInPence(123456))

      val completeReturn = sample[CompleteReturn].copy(yearToDateLiabilityAnswers = sample[CompleteYearToDateLiabilityAnswers]
        .copy(hasEstimatedDetailsWithCalculatedTaxDue = sample[HasEstimatedDetailsWithCalculatedTaxDue]
          .copy(calculatedTaxDue = calculatedTaxDue)))

      DisposalDetails.apply(completeReturn).initialGain.isDefined shouldBe true
      DisposalDetails.apply(completeReturn).initialLoss.isDefined shouldBe false
    }

    "return none as initialGain and some value for initialLoss" in {
      val calculatedTaxDue = sample[GainCalculatedTaxDue].copy(initialGainOrLoss = AmountInPence(-123456))

      val completeReturn = sample[CompleteReturn].copy(yearToDateLiabilityAnswers = sample[CompleteYearToDateLiabilityAnswers]
        .copy(hasEstimatedDetailsWithCalculatedTaxDue = sample[HasEstimatedDetailsWithCalculatedTaxDue]
          .copy(calculatedTaxDue = calculatedTaxDue)))

      DisposalDetails.apply(completeReturn).initialGain.isDefined shouldBe false
      DisposalDetails.apply(completeReturn).initialLoss.isDefined shouldBe true
    }
  }

  "DisposalDetails improvementCosts" must {
    "return some value as improvementCosts" in {
      val completeReturn = sample[CompleteReturn].copy(acquisitionDetails = sample[CompleteAcquisitionDetailsAnswers]
        .copy(improvementCosts = AmountInPence(1234)))

      DisposalDetails.apply(completeReturn).improvementCosts.isDefined shouldBe true
    }

    "return none as improvementCosts" in {
      val completeReturn = sample[CompleteReturn].copy(acquisitionDetails = sample[CompleteAcquisitionDetailsAnswers]
        .copy(improvementCosts = AmountInPence(-1234)))

      DisposalDetails.apply(completeReturn).improvementCosts.isDefined shouldBe false
    }

  }

}
