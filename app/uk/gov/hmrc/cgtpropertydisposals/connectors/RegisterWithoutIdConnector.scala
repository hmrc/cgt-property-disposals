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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, RegistrationDetails, SubscriptionDetails}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegisterWithoutIdConnectorImpl])
trait RegisterWithoutIdConnector {

  def registerWithoutId(registrationDetails: RegistrationDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class RegisterWithoutIdConnectorImpl @Inject()(http: HttpClient, val config: ServicesConfig)(
  implicit ec: ExecutionContext
) extends RegisterWithoutIdConnector
    with DesConnector {
  import uk.gov.hmrc.cgtpropertydisposals.connectors.RegisterWithoutIdConnectorImpl._

  val baseUrl: String = config.baseUrl("register-without-id")

  val url: String = s"$baseUrl/registration/02.00.00/individual"

  override def registerWithoutId(
    registrationDetails: RegistrationDetails
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {
    val registerWithoutIdRequest = RegistrationRequest(
      "CGT",
      isAnAgent = false,
      isAGroup  = false,
      RegistrationIndividual(registrationDetails.name.firstName, registrationDetails.name.lastName),
      toRegistrationAddress(registrationDetails.address),
      RegistrationContactDetails(registrationDetails.emailAddress.value)
    )

    EitherT[Future, Error, HttpResponse](
      http
        .post(url, Json.toJson(registerWithoutIdRequest), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

  private def toRegistrationAddress(address: Address): RegistrationAddress = address match {
    case ukAddress @ UkAddress(line1, line2, town, county, postcode) =>
      RegistrationAddress(line1, line2, town, county, Some(postcode), ukAddress.countryCode)

    case NonUkAddress(line1, line2, line3, line4, postcode, country) =>
      RegistrationAddress(line1, line2, line3, line4, postcode, country.code)

  }

}

object RegisterWithoutIdConnectorImpl {

  final case class RegistrationRequest(
    regime: String,
    isAnAgent: Boolean,
    isAGroup: Boolean,
    individual: RegistrationIndividual,
    address: RegistrationAddress,
    contactDetails: RegistrationContactDetails
  )

  final case class RegistrationIndividual(firstName: String, lastName: String)

  final case class RegistrationContactDetails(emailAddress: String)

  final case class RegistrationAddress(
    line1: String,
    line2: Option[String],
    line3: Option[String],
    line4: Option[String],
    postalCode: Option[String],
    countryCode: String
  )

  implicit val individualWrites: Writes[RegistrationIndividual]         = Json.writes[RegistrationIndividual]
  implicit val addressWrites: Writes[RegistrationAddress]               = Json.writes[RegistrationAddress]
  implicit val contactDetailsWrites: Writes[RegistrationContactDetails] = Json.writes[RegistrationContactDetails]
  implicit val requestWrites: Writes[RegistrationRequest]               = Json.writes[RegistrationRequest]

}
