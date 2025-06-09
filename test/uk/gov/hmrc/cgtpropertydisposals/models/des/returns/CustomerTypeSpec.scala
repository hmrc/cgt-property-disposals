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
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.CustomerType.{Individual, Trust}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails

import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given

class CustomerTypeSpec extends AnyWordSpec with Matchers {

  "CustomerType" must {

    "have a format instance" which {

      "can write JSON correctly" in {
        def test(c: CustomerType, expectedJson: JsValue) = Json.toJson(c) shouldBe expectedJson

        test(Trust, JsString("trust"))
        test(Individual, JsString("individual"))
      }

      "can read JSON correctly" in {
        JsString("trust").validate[CustomerType]      shouldBe JsSuccess(Trust)
        JsString("individual").validate[CustomerType] shouldBe JsSuccess(Individual)
        JsString("???").validate[CustomerType]        shouldBe a[JsError]
        JsNumber(1).validate[CustomerType]            shouldBe a[JsError]
      }
    }

    "have a method which converts from  subscribed details" in {

      def test(name: Either[TrustName, IndividualName], expectedDesCustomerType: CustomerType) = {
        val subscribedDetails = sample[SubscribedDetails].copy(name = name)

        CustomerType(subscribedDetails) shouldBe expectedDesCustomerType
      }

      test(Left(TrustName("")), Trust)
      test(Right(IndividualName("", "")), Individual)
    }

  }

}
