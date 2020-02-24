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
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.LossSummaryDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers

class LossSummaryDetailsSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  "LossSummaryDetails inYearLossUsed" must {
    "return some value for in year losses" in {
      val inYearLossesAmountInPounds: BigDecimal = BigDecimal(1000)
      val completeReturn = sample[CompleteReturn].copy(exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers]
        .copy(inYearLosses = AmountInPence.fromPounds(amount = inYearLossesAmountInPounds)))

      LossSummaryDetails.apply(completeReturn).inYearLossUsed.isDefined shouldBe true
    }

    "return none for no in year losses" in {
      val inYearLossesAmountInPounds: BigDecimal = BigDecimal(-1000)
      val completeReturn = sample[CompleteReturn].copy(exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers]
        .copy(inYearLosses = AmountInPence.fromPounds(amount = inYearLossesAmountInPounds)))

      LossSummaryDetails.apply(completeReturn).inYearLossUsed.isDefined shouldBe false
    }
  }

  "LossSummaryDetails preYearLoss" must {
    "return some value for pre year losses" in {
      val preYearLossesAmountInPounds: BigDecimal = BigDecimal(1000)
      val completeReturn = sample[CompleteReturn].copy(exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers]
        .copy(previousYearsLosses = AmountInPence.fromPounds(amount = preYearLossesAmountInPounds)))

      LossSummaryDetails.apply(completeReturn).preYearLossUsed.isDefined shouldBe true
    }

    "return none for no pre year losses" in {
      val preYearLossesAmountInPounds: BigDecimal = BigDecimal(-1000)
      val completeReturn = sample[CompleteReturn].copy(exemptionsAndLossesDetails = sample[CompleteExemptionAndLossesAnswers]
        .copy(previousYearsLosses = AmountInPence.fromPounds(amount = preYearLossesAmountInPounds)))

      LossSummaryDetails.apply(completeReturn).preYearLossUsed.isDefined shouldBe false
    }
  }
}
