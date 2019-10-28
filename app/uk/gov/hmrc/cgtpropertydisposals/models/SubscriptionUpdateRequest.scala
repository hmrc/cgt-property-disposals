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

package uk.gov.hmrc.cgtpropertydisposals.models

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.SubscriptionUpdateRequest.SubscriptionUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat

final case class SubscriptionUpdateRequest(
  subscriptionDetails: SubscriptionUpdateDetails
)

object SubscriptionUpdateRequest {
  implicit val format: OFormat[SubscriptionUpdateRequest] = Json.format[SubscriptionUpdateRequest]

  final case class SubscriptionUpdateDetails(
    typeOfPersonDetails: Either[TrustName, IndividualName],
    addressDetails: Address,
    contactDetails: ContactDetails
  )

  object SubscriptionUpdateDetails {
    implicit val format: OFormat[SubscriptionUpdateDetails] = Json.format[SubscriptionUpdateDetails]
  }

}
