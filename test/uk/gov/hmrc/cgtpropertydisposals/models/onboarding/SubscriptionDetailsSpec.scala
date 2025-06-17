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

package uk.gov.hmrc.cgtpropertydisposals.models.onboarding

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.name.ContactName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionDetails

import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.given

class SubscriptionDetailsSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "SubscriptionDetails" must {

    "have a method which converts from Registration Details and SapNumber to SubscriptionDetails" in {
      val registrationDetails = sample[RegistrationDetails]
      val sapNumber           = sample[SapNumber]
      SubscriptionDetails.fromRegistrationDetails(registrationDetails, sapNumber) shouldBe SubscriptionDetails(
        Right(registrationDetails.name),
        ContactName(s"${registrationDetails.name.firstName} ${registrationDetails.name.lastName}"),
        registrationDetails.emailAddress,
        registrationDetails.address,
        sapNumber
      )
    }

  }

}
