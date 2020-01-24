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

package uk.gov.hmrc.cgtpropertydisposals.models.des

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import cats.instances.string._
import cats.syntax.eq._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordServiceImpl.Validation

final case class AddressDetails(
  addressLine1: String,
  addressLine2: Option[String],
  addressLine3: Option[String],
  addressLine4: Option[String],
  postalCode: Option[String],
  countryCode: String
)

object AddressDetails {
  implicit val format: OFormat[AddressDetails] = Json.format[AddressDetails]

  def fromDesAddressDetails(
    addressDetails: AddressDetails
  )(desNonIsoCountryCodes: List[CountryCode]): Validation[Address] =
    if (addressDetails.countryCode === "GB") {
      addressDetails.postalCode.fold[ValidatedNel[String, Address]](
        Invalid(NonEmptyList.one("Could not find postcode for UK address"))
      )(p =>
        Valid(
          UkAddress(
            addressDetails.addressLine1,
            addressDetails.addressLine2,
            addressDetails.addressLine3,
            addressDetails.addressLine4,
            p
          )
        )
      )
    } else {
      val country = Country.countryCodeToCountryName.get(addressDetails.countryCode) match {
        case Some(countryName) => Some(Country(addressDetails.countryCode, Some(countryName)))
        case None if desNonIsoCountryCodes.contains(addressDetails.countryCode) =>
          Some(Country(addressDetails.countryCode, None))
        case None => None
      }

      country.fold[ValidatedNel[String, Address]](
        Invalid(NonEmptyList.one(s"Received unknown country code: ${addressDetails.countryCode}"))
      )(c =>
        Valid(
          NonUkAddress(
            addressDetails.addressLine1,
            addressDetails.addressLine2,
            addressDetails.addressLine3,
            addressDetails.addressLine4,
            addressDetails.postalCode,
            c
          )
        )
      )
    }

}
