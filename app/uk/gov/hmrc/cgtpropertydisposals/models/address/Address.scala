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

package uk.gov.hmrc.cgtpropertydisposals.models.address

import cats.Eq
import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.KeyValuePair

sealed trait Address

object Address {

  final case class UkAddress(
    line1: String,
    line2: Option[String],
    town: Option[String],
    county: Option[String],
    postcode: String
  ) extends Address {
    val countryCode: String = "GB"
  }

  final case class NonUkAddress(
    line1: String,
    line2: Option[String],
    line3: Option[String],
    line4: Option[String],
    postcode: Option[String],
    country: Country
  ) extends Address

  // the format instance using the play-json-derived-codecs library wraps
  // the case class inside a JsObject with case class type name as the key
  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val format: OFormat[Address] = derived.oformat()

  def toAddressDetails(address: Address): AddressDetails =
    address match {
      case u @ UkAddress(line1, line2, town, county, postcode) =>
        AddressDetails(line1, line2, town, county, Some(postcode), u.countryCode)
      case NonUkAddress(line1, line2, line3, line4, postcode, country) =>
        AddressDetails(line1, line2, line3, line4, postcode, country.code)
    }

  def toVerifierFormat(address: Address): KeyValuePair =
    address match {
      case UkAddress(line1, line2, town, county, postcode)             => KeyValuePair("Postcode", postcode)
      case NonUkAddress(line1, line2, line3, line4, postcode, country) => KeyValuePair("CountryCode", country.code)
    }

  implicit val eq: Eq[Address] = Eq.fromUniversalEquals

}
