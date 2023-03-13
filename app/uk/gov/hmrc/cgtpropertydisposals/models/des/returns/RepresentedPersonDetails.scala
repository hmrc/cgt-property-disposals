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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeDetails

import java.time.LocalDate

final case class RepresentedPersonDetails(
  capacitorPersonalRep: String,
  firstName: String,
  lastName: String,
  idType: String,
  idValue: String,
  dateOfBirth: Option[String],
  dateOfDeath: Option[LocalDate],
  trustCessationDate: Option[String],
  trustTerminationDate: Option[String],
  addressDetails: Option[AddressDetails],
  email: Option[String]
)

object RepresentedPersonDetails {

  def apply(representeeDetails: RepresenteeDetails): RepresentedPersonDetails = {
    val idTypeWithValue = representeeDetails.id match {
      case Left(sautr)          => "UTR"  -> sautr.value
      case Right(Left(nino))    => "NINO" -> nino.value
      case Right(Right(cgtRef)) => "ZCGT" -> cgtRef.value
    }

    val answers = representeeDetails.answers

    RepresentedPersonDetails(
      answers.dateOfDeath.fold("Capacitor")(_ => "Personal Representative"),
      answers.name.firstName,
      answers.name.lastName,
      idTypeWithValue._1,
      idTypeWithValue._2,
      None,
      answers.dateOfDeath.map(_.value),
      None,
      None,
      Some(Address.toAddressDetails(representeeDetails.answers.contactDetails.address)),
      Some(answers.contactDetails.emailAddress.value)
    )
  }

  implicit val representedPersonDetailsFormat: OFormat[RepresentedPersonDetails] =
    Json.format[RepresentedPersonDetails]
}
