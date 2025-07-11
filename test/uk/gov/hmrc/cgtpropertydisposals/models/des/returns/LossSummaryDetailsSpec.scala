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
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.generators.CompleteReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers

class LossSummaryDetailsSpec extends AnyWordSpec with Matchers {

  "LossSummaryDetails" must {
    "return some value for in year losses when a non-zero value is found" in {
      val inYearLossesAmountInPounds: BigDecimal = BigDecimal(1000)
      val result                                 = LossSummaryDetails(
        sample[CompleteExemptionAndLossesAnswers]
          .copy(inYearLosses = AmountInPence.fromPounds(amount = inYearLossesAmountInPounds))
      )
      result.inYearLoss     shouldBe true
      result.inYearLossUsed shouldBe Some(inYearLossesAmountInPounds)
    }

    "return none for no in year losses when a zero value is found" in {
      val result = LossSummaryDetails(
        sample[CompleteExemptionAndLossesAnswers]
          .copy(inYearLosses = AmountInPence.zero)
      )
      result.inYearLoss     shouldBe false
      result.inYearLossUsed shouldBe None
    }

    "return some value for pre year losses when a non-zero value can be found" in {
      val preYearLossesAmountInPounds: BigDecimal = BigDecimal(1000)
      val result                                  = LossSummaryDetails(
        sample[CompleteExemptionAndLossesAnswers]
          .copy(previousYearsLosses = AmountInPence.fromPounds(amount = preYearLossesAmountInPounds))
      )
      result.preYearLoss     shouldBe true
      result.preYearLossUsed shouldBe Some(preYearLossesAmountInPounds)
    }

    "return none for no pre year losses when a zero value is used" in {
      val result = LossSummaryDetails(
        sample[CompleteExemptionAndLossesAnswers]
          .copy(previousYearsLosses = AmountInPence.zero)
      )
      result.preYearLoss     shouldBe false
      result.preYearLossUsed shouldBe None
    }
  }
}
