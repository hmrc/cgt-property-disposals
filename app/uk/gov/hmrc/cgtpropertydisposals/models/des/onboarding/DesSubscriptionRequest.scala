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

package uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest.{DesSubscriptionDetails, Identity}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, TypeOfPersonDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionDetails

final case class DesSubscriptionRequest(
  regime: String,
  identity: Identity,
  subscriptionDetails: DesSubscriptionDetails
)

object DesSubscriptionRequest {

  def apply(s: SubscriptionDetails): DesSubscriptionRequest = {
    val typeOfPersonDetails = s.name.fold(
      trustName => TypeOfPersonDetails.Trustee(trustName.value),
      individualName => TypeOfPersonDetails.Individual(individualName.firstName, individualName.lastName)
    )

    DesSubscriptionRequest(
      "CGT",
      Identity("sapNumber", s.sapNumber.value),
      DesSubscriptionDetails(
        typeOfPersonDetails,
        Address.toAddressDetails(s.address),
        ContactDetails(s.contactName.value, s.emailAddress.value)
      )
    )
  }

  final case class Identity(idType: String, idValue: String)

  final case class ContactDetails(
    contactName: String,
    emailAddress: String
  )

  final case class DesSubscriptionDetails(
    typeOfPersonDetails: TypeOfPersonDetails,
    addressDetails: AddressDetails,
    contactDetails: ContactDetails
  )

  implicit val identityWrites: Writes[Identity]                             = Json.writes[Identity]
  implicit val contactDetailsWrites: Writes[ContactDetails]                 = Json.writes[ContactDetails]
  implicit val desSubscriptionDetailsWrites: Writes[DesSubscriptionDetails] = Json.writes[DesSubscriptionDetails]
  implicit val subscriptionRequestWrites: Writes[DesSubscriptionRequest]    = Json.writes[DesSubscriptionRequest]

}
