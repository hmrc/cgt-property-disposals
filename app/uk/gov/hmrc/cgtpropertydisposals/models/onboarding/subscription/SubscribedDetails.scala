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

package uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.TelephoneNumber
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat

final case class SubscribedDetails(
  name: Either[TrustName, IndividualName],
  emailAddress: Email,
  address: Address,
  contactName: ContactName,
  cgtReference: CgtReference,
  telephoneNumber: Option[TelephoneNumber],
  registeredWithId: Boolean
)

object SubscribedDetails {

  def apply(submitReturnRequest: SubmitReturnRequest): String =
    submitReturnRequest.subscribedDetails.name.fold(_ => "trust", _ => "individual")

  implicit val format: Format[SubscribedDetails] = Json.format[SubscribedDetails]

}
