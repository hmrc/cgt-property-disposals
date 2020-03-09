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

package uk.gov.hmrc.cgtpropertydisposals.models.finance

import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.ChargeType._

class ChargeTypeSpec extends WordSpec with Matchers {

  "ChargeType" must {

    "have a method which converts strings to a ChargeType" in {
      ChargeType.fromString("CGT PPD Return UK Resident")     shouldBe Right(UkResidentReturn)
      ChargeType.fromString("CGT PPD Return Non UK Resident") shouldBe Right(NonUkResidentReturn)
      ChargeType.fromString("CGT PPD Interest")               shouldBe Right(Interest)
      ChargeType.fromString("CGT PPD Late Filing Penalty")    shouldBe Right(LateFilingPenalty)
      ChargeType.fromString("CGT PPD 6 Mth LFP")              shouldBe Right(SixMonthLateFilingPenalty)
      ChargeType.fromString("CGT PPD 12 Mth LFP")             shouldBe Right(TwelveMonthLateFilingPenalty)
      ChargeType.fromString("CGT PPD Late Payment Penalty")   shouldBe Right(LatePaymentPenalty)
      ChargeType.fromString("CGT PPD 6 Mth LPP")              shouldBe Right(SixMonthLatePaymentPenalty)
      ChargeType.fromString("CGT PPD 12 Mth LPP")             shouldBe Right(TwelveMonthLatePaymentPenalty)
      ChargeType.fromString("CGT PPD Penalty Interest")       shouldBe Right(PenaltyInterest)
      ChargeType.fromString("abc").isLeft                     shouldBe true
    }

  }

}
