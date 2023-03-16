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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionConnectorImpl])
trait SubscriptionConnector {

  def subscribe(subscriptionDetails: DesSubscriptionRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def getSubscription(cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def updateSubscription(subscribedDetails: DesSubscriptionUpdateRequest, cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def getSubscriptionStatus(sapNumber: SapNumber)(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse]

}

@Singleton
class SubscriptionConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends SubscriptionConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("subscription")

  val subscribeUrl: String = s"$baseUrl/subscriptions/create/CGT"

  def subscriptionUrl(cgtReference: CgtReference): String =
    s"$baseUrl/subscriptions/CGT/ZCGT/${cgtReference.value}"

  def subscriptionStatusUrl(sapNumber: SapNumber): String =
    s"$baseUrl/cross-regime/subscription/CGT/${sapNumber.value}/status"

  override def subscribe(
    subscriptionRequest: DesSubscriptionRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .POST[JsValue, HttpResponse](subscribeUrl, Json.toJson(subscriptionRequest), headers)(
          implicitly[Writes[JsValue]],
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

  override def getSubscription(cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](subscriptionUrl(cgtReference), Seq.empty, headers)(
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e =>
          Left(Error(e))
        }
    )

  override def updateSubscription(subscriptionRequest: DesSubscriptionUpdateRequest, cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .PUT[JsValue, HttpResponse](
          subscriptionUrl(cgtReference),
          Json.toJson(subscriptionRequest),
          headers
        )(
          implicitly[Writes[JsValue]],
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e =>
          Left(Error(e))
        }
    )

  override def getSubscriptionStatus(sapNumber: SapNumber)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](
          subscriptionStatusUrl(sapNumber),
          Seq.empty,
          headers
        )(
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

}
