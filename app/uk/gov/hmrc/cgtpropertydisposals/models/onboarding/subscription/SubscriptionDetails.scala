/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.subscription

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat
import uk.gov.hmrc.cgtpropertydisposals.models.ids.SapNumber
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails

final case class SubscriptionDetails(
  name: Either[TrustName, IndividualName],
  contactName: ContactName,
  emailAddress: Email,
  address: Address,
  sapNumber: SapNumber
)
object SubscriptionDetails {
  implicit val format: Format[SubscriptionDetails] = Json.format[SubscriptionDetails]

  def fromRegistrationDetails(registrationDetails: RegistrationDetails, sapNumber: SapNumber): SubscriptionDetails =
    SubscriptionDetails(
      Right(registrationDetails.name),
      ContactName(s"${registrationDetails.name.firstName} ${registrationDetails.name.lastName}"),
      registrationDetails.emailAddress,
      registrationDetails.address,
      sapNumber
    )

}
