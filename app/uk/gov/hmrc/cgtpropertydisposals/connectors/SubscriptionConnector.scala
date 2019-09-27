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
import play.api.libs.json.{JsString, JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.SubscriptionConnectorImpl.{SubscriptionRequest, TypeOfPerson}
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, Error, SubscriptionDetails}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionConnectorImpl])
trait SubscriptionConnector {

  def subscribe(subscriptionDetails: SubscriptionDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class SubscriptionConnectorImpl @Inject()(http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends SubscriptionConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("subscription")

  val url: String = s"$baseUrl/subscribe"

  override def subscribe(
    subscriptionDetails: SubscriptionDetails
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {
    val subscriptionRequest = SubscriptionRequest(
      subscriptionDetails.contactName.fold[TypeOfPerson](_ => TypeOfPerson.Trustee, _ => TypeOfPerson.Individual),
      subscriptionDetails.contactName.fold(_.value, n => s"${n.firstName} ${n.lastName}"),
      subscriptionDetails.emailAddress,
      subscriptionDetails.address,
      subscriptionDetails.sapNumber
    )

    EitherT[Future, Error, HttpResponse](
      http
        .post(url, Json.toJson(subscriptionRequest), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

}

object SubscriptionConnectorImpl {

  sealed trait TypeOfPerson { val value: Int }

  object TypeOfPerson {

    final case object Individual extends TypeOfPerson {
      val value = 1
    }

    final case object Trustee extends TypeOfPerson {
      val value = 2
    }

  }
  final case class SubscriptionRequest(
    typeOfPerson: TypeOfPerson,
    contactName: String,
    emailAddress: String,
    address: Address,
    sapNumber: String
  )

  implicit val typeOfPersonWrites: Writes[TypeOfPerson]               = Writes(p => JsString(p.value.toString))
  implicit val subscriptionRequestWrites: Writes[SubscriptionRequest] = Json.writes[SubscriptionRequest]
}
