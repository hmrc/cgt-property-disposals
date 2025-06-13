/*
 * Copyright 2025 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.generators

import org.scalacheck.Gen
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import io.github.martinhh.derived.scalacheck.given
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.SubscriptionSuccessful

object OnboardingGen extends GenUtils {
  given registrationDetailsGen: Gen[RegistrationDetails] = gen[RegistrationDetails]

  given subscriptionDetailsGen: Gen[SubscriptionDetails] = gen[SubscriptionDetails]

  given subscriptionSuccessfulGen: Gen[SubscriptionSuccessful] = gen[SubscriptionSuccessful]

  given subscribedDetailsGen: Gen[SubscribedDetails] = gen[SubscribedDetails]

  given desSubscriptionRequestGen: Gen[DesSubscriptionRequest] = gen[DesSubscriptionRequest]

  given desUpdateRequestGen: Gen[DesSubscriptionUpdateRequest] = gen[DesSubscriptionUpdateRequest]

}
