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

package uk.gov.hmrc.cgtpropertydisposals.models.name

import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordServiceImpl.Validation
import uk.gov.hmrc.cgtpropertydisposals.service.SubscriptionService.TypeOfPersonDetails
import cats.implicits._

object Name {

  def nameValidation(
    typeOfPersonDetails: TypeOfPersonDetails
  ): Validation[Either[TrustName, IndividualName]] =
    if (typeOfPersonDetails.typeOfPerson === "Individual") {
      typeOfPersonDetails.firstName -> typeOfPersonDetails.lastName match {
        case (Some(firstName), Some(lastName)) => Valid(Right(IndividualName(firstName, lastName)))
        case (Some(_), None) =>
          Invalid(NonEmptyList.one("Subscription Display response did not contain last name"))
        case (None, Some(_)) =>
          Invalid(NonEmptyList.one("Subscription Display response did not contain first name"))
        case _ => Invalid(NonEmptyList.one("Subscription Display response did not contain first name or last name"))
      }
    } else if (typeOfPersonDetails.typeOfPerson === "Trustee") typeOfPersonDetails.organisationName match {
      case Some(organisationName) => Valid(Left(TrustName(organisationName)))
      case _                      => Invalid(NonEmptyList.one("Subscription Display response did not contain organisation name"))
    } else {
      Invalid(NonEmptyList.one("BPR contained contained neither an organisation name or an individual name"))
    }

}
