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

package uk.gov.hmrc.cgtpropertydisposals.models.des

import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.models.SubscriptionDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address

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

    val addressDetails = s.address match {
      case ukAddress @ Address.UkAddress(line1, line2, town, county, postcode) =>
        AddressDetails(line1, line2, town, county, Some(postcode), ukAddress.countryCode)
      case Address.NonUkAddress(line1, line2, line3, line4, postcode, country) =>
        AddressDetails(line1, line2, line3, line4, postcode, country.code)
    }

    DesSubscriptionRequest(
      "CGT",
      Identity("sapNumber", s.sapNumber.value),
      DesSubscriptionDetails(
        typeOfPersonDetails,
        addressDetails,
        ContactDetails(s.contactName.value, s.emailAddress.value)
      )
    )
  }

  implicit val subscriptionRequestWrites: Writes[DesSubscriptionRequest] = Json.writes[DesSubscriptionRequest]

}
