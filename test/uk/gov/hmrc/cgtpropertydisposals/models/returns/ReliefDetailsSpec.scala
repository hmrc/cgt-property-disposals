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
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.ReliefDetails
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.OtherReliefsOption.OtherReliefs
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers

class ReliefDetailsSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  "ReliefDetails reliefs" must {

    "return false for no reliefs" in {
      val zeroReliefDetails = CompleteReliefDetailsAnswers(AmountInPence.zero, AmountInPence.zero, None)
      val completeReturn    = sample[CompleteReturn].copy(reliefDetails = zeroReliefDetails)

      ReliefDetails.apply(completeReturn).reliefs shouldBe false
    }

    "return true if privateResidentsRelief exist" in {
      val privateResidentsRelief = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence(1000L),
        lettingsRelief         = AmountInPence.zero,
        otherReliefs           = None
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = privateResidentsRelief)

      ReliefDetails.apply(completeReturn).reliefs shouldBe false
    }

    "return true if lettingsRelief exist" in {
      val lettingsRelief = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence.zero,
        lettingsRelief         = AmountInPence(1000L),
        otherReliefs           = None
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = lettingsRelief)

      ReliefDetails.apply(completeReturn).reliefs shouldBe false
    }

    "return true if any other reliefs exist" in {
      val otherReliefs = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence.zero,
        lettingsRelief         = AmountInPence.zero,
        otherReliefs           = Some(OtherReliefs("name: String", AmountInPence.zero))
      )

      val completeReturn = sample[CompleteReturn].copy(reliefDetails = otherReliefs)

      ReliefDetails.apply(completeReturn).reliefs shouldBe false
    }
  }

  "ReliefDetails lettingsRelief" must {

    "return option as letting relief value in pounds" in {
      val amountInPounds = 1000
      val reliefDetails = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence.zero,
        lettingsRelief         = AmountInPence.fromPounds(amountInPounds),
        otherReliefs           = None
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = reliefDetails)

      ReliefDetails.apply(completeReturn).lettingsReflief shouldBe Some(amountInPounds)
    }

    "return None as letting relief value in pounds" in {
      val zeroReliefDetails = CompleteReliefDetailsAnswers(AmountInPence.zero, AmountInPence.zero, None)
      val completeReturn    = sample[CompleteReturn].copy(reliefDetails = zeroReliefDetails)

      ReliefDetails.apply(completeReturn).lettingsReflief shouldBe None
    }
  }

  "ReliefDetails privateResRelief" must {

    "return option as privateResRelief value in pounds" in {
      val amountInPounds = 1000
      val reliefDetails = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence.fromPounds(amountInPounds),
        lettingsRelief         = AmountInPence.zero,
        otherReliefs           = None
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = reliefDetails)

      ReliefDetails.apply(completeReturn).privateResRelief shouldBe Some(amountInPounds)
    }

    "return None as privateResRelief relief value in pounds" in {
      val zeroReliefDetails = CompleteReliefDetailsAnswers(AmountInPence.zero, AmountInPence.zero, None)
      val completeReturn    = sample[CompleteReturn].copy(reliefDetails = zeroReliefDetails)

      ReliefDetails.apply(completeReturn).privateResRelief shouldBe None
    }
  }

  "ReliefDetails giftHoldOverRelief" must {
    "return None as giftHoldOverRelief value" in {
      val amountInPounds = 1000
      val reliefDetails = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence.fromPounds(amountInPounds),
        lettingsRelief         = AmountInPence.zero,
        otherReliefs           = None
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = reliefDetails)

      ReliefDetails.apply(completeReturn).giftHoldOverRelief shouldBe None
    }
  }

  "ReliefDetails otherRelief" must {
    "return options as otherRelief name and amount values" in {
      val otherReliefAmountInPounds = 1000
      val otherReliefName           = "otherReliefName"
      val reliefDetails = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence.zero,
        lettingsRelief         = AmountInPence.zero,
        otherReliefs           = Some(OtherReliefs(otherReliefName, AmountInPence.fromPounds(otherReliefAmountInPounds)))
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = reliefDetails)

      ReliefDetails.apply(completeReturn).otherRelief       shouldBe Some(otherReliefName)
      ReliefDetails.apply(completeReturn).otherReliefAmount shouldBe Some(otherReliefAmountInPounds)
    }

    "return none as otherRelief name and amount" in {
      val reliefDetails = CompleteReliefDetailsAnswers(
        privateResidentsRelief = AmountInPence(10000),
        lettingsRelief         = AmountInPence(10000),
        otherReliefs           = None
      )
      val completeReturn = sample[CompleteReturn].copy(reliefDetails = reliefDetails)

      ReliefDetails.apply(completeReturn).otherRelief       shouldBe None
      ReliefDetails.apply(completeReturn).otherReliefAmount shouldBe None
    }
  }
}
