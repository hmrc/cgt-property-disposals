/*
 * Copyright 2024 HM Revenue & Customs
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
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.KeyValuePair
import play.api.libs.json._

sealed trait Address

object Address {

  final case class UkAddress(
    line1: String,
    line2: Option[String],
    town: Option[String],
    county: Option[String],
    postcode: Postcode
  ) extends Address {
    val countryCode: String = Country.uk.code
  }

  final case class NonUkAddress(
    line1: String,
    line2: Option[String],
    line3: Option[String],
    line4: Option[String],
    postcode: Option[Postcode],
    country: Country
  ) extends Address

  // the format instance using the play-json-derived-codecs library wraps
  // the case class inside a JsObject with case class type name as the key
  implicit val ukAddressFormat: OFormat[UkAddress]       = Json.format[UkAddress]
  implicit val nonUkAddressFormat: OFormat[NonUkAddress] = Json.format[NonUkAddress]

  implicit val format: OFormat[Address] = new OFormat[Address] {
    override def reads(json: JsValue): JsResult[Address] =
      (json \ "UkAddress", json \ "NonUkAddress") match {
        case (JsDefined(ukJson), _)    => ukJson.validate[UkAddress]
        case (_, JsDefined(nonUkJson)) => nonUkJson.validate[NonUkAddress]
        case _                         =>
          (json \ "_type").validate[String] match {
            case JsSuccess("uk", _)    => json.validate[UkAddress]
            case JsSuccess("nonUk", _) => json.validate[NonUkAddress]
            case JsSuccess(other, _)   => JsError(s"Unknown _type: $other")
            case JsError(_)            =>
              if (json \ "countryCode").isDefined then json.validate[NonUkAddress]
              else json.validate[UkAddress]
          }
      }

    override def writes(address: Address): JsObject = address match {
      case uk: UkAddress       => Json.obj("UkAddress" -> Json.toJson(uk)(ukAddressFormat))
      case nonUk: NonUkAddress => Json.obj("NonUkAddress" -> Json.toJson(nonUk)(nonUkAddressFormat))
    }
  }

  def toAddressDetails(address: Address): AddressDetails =
    address match {
      case u @ UkAddress(line1, line2, town, county, postcode)         =>
        AddressDetails(
          line1,
          line2,
          town,
          county,
          Some(postcode.stripAllSpaces().toUpperCase()),
          u.countryCode
        )
      case NonUkAddress(line1, line2, line3, line4, postcode, country) =>
        AddressDetails(
          line1,
          line2,
          line3,
          line4,
          postcode.map(_.stripAllSpaces().toUpperCase()),
          country.code
        )
    }

  def toVerifierFormat(address: Address): KeyValuePair =
    address match {
      case UkAddress(_, _, _, _, postcode)      => KeyValuePair("Postcode", postcode.value)
      case NonUkAddress(_, _, _, _, _, country) => KeyValuePair("CountryCode", country.code)
    }

  implicit val eq: Eq[Address] = Eq.fromUniversalEquals

}
