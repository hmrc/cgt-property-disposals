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

package uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription

import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.{EitherFormat, Email, TelephoneNumber}

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

  implicit val eitherFormat: Format[Either[TrustName, IndividualName]] =
    EitherFormat.eitherFormat[TrustName, IndividualName]
  implicit val format: Format[SubscribedDetails]                       = Json.format[SubscribedDetails]

}
