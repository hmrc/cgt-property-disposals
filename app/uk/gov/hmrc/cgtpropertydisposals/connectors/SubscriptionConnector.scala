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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.SubscriptionConnectorImpl.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesSubscriptionUpdateRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.subscription.{SubscribedDetails, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionConnectorImpl])
trait SubscriptionConnector {

  def subscribe(subscriptionDetails: SubscriptionDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def getSubscription(cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def updateSubscription(subscribedDetails: SubscribedDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class SubscriptionConnectorImpl @Inject()(http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends SubscriptionConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("subscription")

  val subscribeUrl: String = s"$baseUrl/subscriptions/create/CGT"
  def subscriptionDisplayUrl(cgtReference: CgtReference): String =
    s"$baseUrl/subscriptions/CGT/ZCGT/${cgtReference.value}"

  override def subscribe(
    subscriptionDetails: SubscriptionDetails
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {
    val subscriptionRequest = DesSubscriptionRequest(subscriptionDetails)

    EitherT[Future, Error, HttpResponse](
      http
        .post(subscribeUrl, Json.toJson(subscriptionRequest), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

  override def getSubscription(cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .get(subscriptionDisplayUrl(cgtReference), Map.empty, headers)(
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover {
          case e => Left(Error(e))
        }
    )

  override def updateSubscription(subscribedDetails: SubscribedDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .put(
          subscriptionDisplayUrl(subscribedDetails.cgtReference),
          DesSubscriptionUpdateRequest(subscribedDetails),
          headers)(
          implicitly[Writes[DesSubscriptionUpdateRequest]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover {
          case e => Left(Error(e))
        }
    )

}

object SubscriptionConnectorImpl {

  final case class Identity(idType: String, idValue: String)

  sealed trait TypeOfPersonDetails extends Product with Serializable

  object TypeOfPersonDetails {

    final case class Individual(firstName: String, lastName: String, typeOfPerson: String = "Individual")
        extends TypeOfPersonDetails

    final case class Trustee(organisationName: String, typeOfPerson: String = "Trustee") extends TypeOfPersonDetails

    implicit val typeOfPersonWrites: Writes[TypeOfPersonDetails] = Writes {
      case i: Individual => Json.writes[Individual].writes(i)
      case t: Trustee    => Json.writes[Trustee].writes(t)
    }

  }

  final case class ContactDetails(
    contactName: String,
    emailAddress: String
  )

  final case class DesSubscriptionDetails(
    typeOfPersonDetails: TypeOfPersonDetails,
    addressDetails: AddressDetails,
    contactDetails: ContactDetails
  )

  final case class DesSubscriptionRequest(
    regime: String,
    identity: Identity,
    subscriptionDetails: DesSubscriptionDetails
  )

  object DesSubscriptionRequest {

    def apply(s: SubscriptionDetails): DesSubscriptionRequest = {
      val typeOfPersonDetails = s.name.fold(
        trustName => TypeOfPersonDetails.Trustee(trustName.value),
        individualName => TypeOfPersonDetails.Individual(individualName.firstName, individualName.lastName)
      )

      val addressDetails = s.address match {
        case ukAddress @ Address.UkAddress(line1, line2, town, county, postcode) =>
          AddressDetails(line1, line2, town, county, Some(postcode), ukAddress.countryCode)
        case Address.NonUkAddress(line1, line2, line3, line4, postcode, country) =>
          AddressDetails(line1, line2, line3, line4, postcode, country.code)
      }

      DesSubscriptionRequest(
        "CGT",
        Identity("sapNumber", s.sapNumber.value),
        DesSubscriptionDetails(
          typeOfPersonDetails,
          addressDetails,
          ContactDetails(s.contactName.value, s.emailAddress.value)
        )
      )
    }

  }

  implicit val identityWrites: Writes[Identity]                             = Json.writes[Identity]
  implicit val contactDetailsWrites: Writes[ContactDetails]                 = Json.writes[ContactDetails]
  implicit val desSubscriptionDetailsWrites: Writes[DesSubscriptionDetails] = Json.writes[DesSubscriptionDetails]
  implicit val subscriptionRequestWrites: Writes[DesSubscriptionRequest]    = Json.writes[DesSubscriptionRequest]
}
