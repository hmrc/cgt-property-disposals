/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.des

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest.DesSubscriptionUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.TypeOfPersonDetails._
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails

final case class DesSubscriptionUpdateRequest(
  regime: String,
  subscriptionDetails: DesSubscriptionUpdateDetails
)

object DesSubscriptionUpdateRequest {
  implicit val format: Writes[DesSubscriptionUpdateRequest] = Json.writes[DesSubscriptionUpdateRequest]

  final case class DesSubscriptionUpdateDetails(
    typeOfPersonDetails: TypeOfPersonDetails,
    addressDetails: AddressDetails,
    contactDetails: ContactDetails
  )

  object DesSubscriptionUpdateDetails {
    implicit val writes: Writes[DesSubscriptionUpdateDetails] = Json.writes[DesSubscriptionUpdateDetails]
  }

  def apply(subscribedDetails: SubscribedDetails): DesSubscriptionUpdateRequest = {
    val typeOfPerson: TypeOfPersonDetails =
      subscribedDetails.name.fold(
        trustName => Trustee(trustName.value),
        individualName => Individual(individualName.firstName, individualName.lastName)
      )

    val contactDetails = ContactDetails(
      subscribedDetails.contactName.value,
      subscribedDetails.telephoneNumber.map(telephoneNumber => telephoneNumber.value),
      None,
      None,
      Some(subscribedDetails.emailAddress.value)
    )

    DesSubscriptionUpdateRequest(
      "CGT",
      DesSubscriptionUpdateDetails(
        typeOfPerson,
        Address.toAddressDetails(subscribedDetails.address),
        contactDetails
      )
    )
  }
}
