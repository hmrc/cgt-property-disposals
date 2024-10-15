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
import play.api.libs.json.Json
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

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
class SubscriptionConnectorImpl @Inject() (http: HttpClientV2, val config: ServicesConfig)(implicit
  ec: ExecutionContext
) extends SubscriptionConnector
    with DesConnector {
  private val baseUrl = config.baseUrl("subscription")

  private val subscribeUrl = s"$baseUrl/subscriptions/create/CGT"

  private def subscriptionUrl(cgtReference: CgtReference) =
    s"$baseUrl/subscriptions/CGT/ZCGT/${cgtReference.value}"

  private def subscriptionStatusUrl(sapNumber: SapNumber) =
    s"$baseUrl/cross-regime/subscription/CGT/${sapNumber.value}/status"

  def subscribe(
    subscriptionRequest: DesSubscriptionRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .post(url"$subscribeUrl")
        .withBody(Json.toJson(subscriptionRequest))
        .setHeader(headers: _*)
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

  def getSubscription(cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .get(url"${subscriptionUrl(cgtReference)}")
        .setHeader(headers: _*)
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e =>
          Left(Error(e))
        }
    )

  def updateSubscription(subscriptionRequest: DesSubscriptionUpdateRequest, cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .put(url"${subscriptionUrl(cgtReference)}")
        .withBody(Json.toJson(subscriptionRequest))
        .setHeader(headers: _*)
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e =>
          Left(Error(e))
        }
    )

  def getSubscriptionStatus(sapNumber: SapNumber)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .get(url"${subscriptionStatusUrl(sapNumber)}")
        .setHeader(headers: _*)
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
}
