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

package uk.gov.hmrc.cgtpropertydisposals.service

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.connectors.SubscriptionConnector
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, ContactDetails, Individual, Trustee}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, Name, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, SubscribedDetails, SubscriptionDetails, SubscriptionResponse, TelephoneNumber}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordServiceImpl.Validation
import uk.gov.hmrc.cgtpropertydisposals.service.SubscriptionService.DesSubscriptionDisplayDetails
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionServiceImpl])
trait SubscriptionService {

  def subscribe(subscriptionDetails: SubscriptionDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscriptionResponse]

  def getSubscription(cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscribedDetails]
}

@Singleton
class SubscriptionServiceImpl @Inject()(connector: SubscriptionConnector, config: Configuration)(
  implicit ec: ExecutionContext
) extends SubscriptionService {

  override def subscribe(
    subscriptionDetails: SubscriptionDetails
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubscriptionResponse] =
    connector.subscribe(subscriptionDetails).subflatMap { response =>
      if (response.status === 200) {
        response.parseJSON[SubscriptionResponse]().leftMap(Error(_))
      } else {
        Left(Error(s"call to subscribe came back with status ${response.status}"))
      }
    }

  override def getSubscription(cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscribedDetails] =
    connector.getSubscription(cgtReference).subflatMap { response =>
      lazy val identifiers =
        List(
          "id"                -> cgtReference.value,
          "DES CorrelationId" -> response.header("CorrelationId").getOrElse("-")
        )

      if (response.status === 200)
        response
          .parseJSON[DesSubscriptionDisplayDetails]()
          .flatMap(toSubscriptionDisplayRecord(_, cgtReference))
          .leftMap(Error(_, identifiers: _*))
      else {
        Left(Error(s"call to subscription display api came back with status ${response.status}"))
      }
    }

  def toSubscriptionDisplayRecord(
    desSubscriptionDisplayDetails: DesSubscriptionDisplayDetails,
    cgtReference: CgtReference
  ): Either[String, SubscribedDetails] = {

    val desNonIsoCountryCodes: List[CountryCode] =
      config.underlying.get[List[CountryCode]]("des.non-iso-country-codes").value

    val addressValidation: Validation[Address] = AddressDetails.fromDesAddressDetails(
      desSubscriptionDisplayDetails.subscriptionDetails.addressDetails
    )(desNonIsoCountryCodes)

    val nameValidation: Validation[Either[TrustName, IndividualName]] = Name.nameValidation(
      desSubscriptionDisplayDetails.subscriptionDetails.individual,
      desSubscriptionDisplayDetails.subscriptionDetails.trustee
    )

    val emailValidation: Validation[Email] =
      Email.emailValidation(desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.emailAddress)

    (addressValidation, nameValidation, emailValidation)
      .mapN {
        case (address, name, email) =>
          SubscribedDetails(
            name,
            email,
            address,
            ContactName(desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.contactName),
            cgtReference,
            desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.phoneNumber
              .map(t => TelephoneNumber(t)),
            desSubscriptionDisplayDetails.subscriptionDetails.isRegisteredWithId
          )
      }
      .toEither
      .leftMap(errors => s"Could not read DES response: ${errors.toList.mkString("; ")}")
  }

}

object SubscriptionService {

  import play.api.libs.json.{Json, OFormat}

  final case class DesSubscriptionDisplayDetails(
    regime: String,
    subscriptionDetails: DesSubscribedDetails
  )

  object DesSubscriptionDisplayDetails {
    implicit val format: OFormat[DesSubscriptionDisplayDetails] = Json.format[DesSubscriptionDisplayDetails]
  }

  final case class DesSubscribedDetails(
    individual: Option[Individual],
    trustee: Option[Trustee],
    isRegisteredWithId: Boolean,
    addressDetails: AddressDetails,
    contactDetails: ContactDetails
  )

  object DesSubscribedDetails {
    implicit val format: OFormat[DesSubscribedDetails] = Json.format[DesSubscribedDetails]
  }

}
