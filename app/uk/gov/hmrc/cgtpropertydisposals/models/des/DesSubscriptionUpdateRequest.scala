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

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.SubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest.DesSubscriptionUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat

final case class DesSubscriptionUpdateRequest(
  regime: String,
  subscriptionDetails: DesSubscriptionUpdateDetails
)

object DesSubscriptionUpdateRequest {
  implicit val format: OFormat[DesSubscriptionUpdateRequest] = Json.format[DesSubscriptionUpdateRequest]

  final case class DesSubscriptionUpdateDetails(
    typeOfPersonDetails: Either[Trustee, Individual],
    addressDetails: AddressDetails,
    contactDetails: ContactDetails
  )

  object DesSubscriptionUpdateDetails {
    implicit val format: OFormat[DesSubscriptionUpdateDetails] = Json.format[DesSubscriptionUpdateDetails]
  }

  def apply(subscriptionUpdateRequest: SubscriptionUpdateRequest): DesSubscriptionUpdateRequest =
    new DesSubscriptionUpdateRequest(
      subscriptionUpdateRequest.regime,
      DesSubscriptionUpdateDetails(
        subscriptionUpdateRequest.subscriptionDetails.typeOfPersonDetails,
        Address.toAddressDetails(subscriptionUpdateRequest.subscriptionDetails.addressDetails),
        subscriptionUpdateRequest.subscriptionDetails.contactDetails
      )
    )
}
