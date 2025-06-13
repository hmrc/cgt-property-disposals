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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import cats.data.EitherT
import cats.instances.char.*
import cats.syntax.eq.*
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.RegistrationDetails
import uk.gov.hmrc.http.HttpReads.Implicits.*
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import play.api.libs.ws.WSBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.RegisterWithoutIdConnectorImpl.{RegistrationAddress, RegistrationContactDetails, RegistrationIndividual, RegistrationRequest}

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[RegisterWithoutIdConnectorImpl])
trait RegisterWithoutIdConnector {
  def registerWithoutId(registrationDetails: RegistrationDetails, acknowledgementReferenceId: UUID)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class RegisterWithoutIdConnectorImpl @Inject() (http: HttpClientV2, val config: ServicesConfig)(implicit
  ec: ExecutionContext
) extends RegisterWithoutIdConnector
    with DesConnector {
  private val baseUrl = config.baseUrl("register-without-id")

  private val url = s"$baseUrl/registration/02.00.00/individual"

  def registerWithoutId(
    registrationDetails: RegistrationDetails,
    acknowledgementReferenceId: UUID
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {
    val acknowledgementRef       = acknowledgementReferenceId.toString.filterNot(_ === '-')
    val registerWithoutIdRequest = RegistrationRequest(
      "CGT",
      acknowledgementRef,
      isAnAgent = false,
      isAGroup = false,
      RegistrationIndividual(registrationDetails.name.firstName, registrationDetails.name.lastName),
      toRegistrationAddress(registrationDetails.address),
      RegistrationContactDetails(registrationDetails.emailAddress.value)
    )

    EitherT[Future, Error, HttpResponse] {
      val request = Json.toJson(registerWithoutIdRequest)
      val result  =
        http
          .post(url"$url")
          .withBody(request)
          .setHeader(headers*)
          .execute[HttpResponse]
      result
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    }

  }

  private def toRegistrationAddress(address: Address) =
    address match {
      case ukAddress @ UkAddress(line1, line2, town, county, postcode) =>
        RegistrationAddress(line1, line2, town, county, Some(postcode.value), ukAddress.countryCode)

      case NonUkAddress(line1, line2, line3, line4, postcode, country) =>
        RegistrationAddress(line1, line2, line3, line4, postcode.map(_.value), country.code)
    }
}

object RegisterWithoutIdConnectorImpl {
  final case class RegistrationRequest(
    regime: String,
    acknowledgementReference: String,
    isAnAgent: Boolean,
    isAGroup: Boolean,
    individual: RegistrationIndividual,
    address: RegistrationAddress,
    contactDetails: RegistrationContactDetails
  )

  final case class RegistrationIndividual(firstName: String, lastName: String)

  final case class RegistrationContactDetails(emailAddress: String)

  final case class RegistrationAddress(
    addressLine1: String,
    addressLine2: Option[String],
    addressLine3: Option[String],
    addressLine4: Option[String],
    postalCode: Option[String],
    countryCode: String
  )

  implicit val individualWrites: Writes[RegistrationIndividual] = Json.writes[RegistrationIndividual]

  implicit val contactDetailsWrites: Writes[RegistrationContactDetails] = Json.writes[RegistrationContactDetails]
  implicit val addressWrites: Writes[RegistrationAddress]               = new Writes[RegistrationAddress] {
    override def writes(o: RegistrationAddress): JsValue = Json.obj(
      "addressLine1" -> o.addressLine1,
      "addressLine2" -> o.addressLine2,
      "addressLine3" -> o.addressLine3,
      "addressLine4" -> o.addressLine4,
      "postalCode"   -> o.postalCode,
      "countryCode"  -> o.countryCode
    )
  }
  implicit val requestWrites: Writes[RegistrationRequest]               = new Writes[RegistrationRequest] {
    override def writes(o: RegistrationRequest): JsValue = Json.obj(
      "regime"                   -> o.regime,
      "acknowledgementReference" -> o.acknowledgementReference,
      "isAnAgent"                -> o.isAnAgent,
      "isAGroup"                 -> o.isAGroup,
      "individual"               -> o.individual,
      "address"                  -> o.address,
      "contactDetails"           -> o.contactDetails
    )
  }
}
