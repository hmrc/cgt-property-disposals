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

package uk.gov.hmrc.cgtpropertydisposals.connectors.returns

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnectorImpl.DesSubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubmitReturnsConnectorImpl])
trait SubmitReturnsConnector {

  def submit(returnRequest: SubmitReturnRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class SubmitReturnsConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends SubmitReturnsConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("returns")

  override def submit(
    returnRequest: SubmitReturnRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {

    val cgtReferenceNumber = returnRequest.subscribedDetails.cgtReference.value
    val returnUrl: String  = s"$baseUrl/capital-gains-tax/cgt-reference/$cgtReferenceNumber/return"

    val desSubmitReturnRequest = DesSubmitReturnRequest(returnRequest)

    EitherT[Future, Error, HttpResponse](
      http
        .post(returnUrl, Json.toJson(desSubmitReturnRequest), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

}

object SubmitReturnsConnectorImpl {

  final case class DesSubmitReturnRequest(ppdReturnDetails: PPDReturnDetails)

  object DesSubmitReturnRequest {

    def apply(submitReturnRequest: SubmitReturnRequest): DesSubmitReturnRequest = {
      val ppdReturnDetails = PPDReturnDetails(submitReturnRequest)
      DesSubmitReturnRequest(ppdReturnDetails)
    }

    implicit val desSubmitReturnRequestFormat: OFormat[DesSubmitReturnRequest] = Json.format[DesSubmitReturnRequest]
  }

}
