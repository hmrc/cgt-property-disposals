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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[ReturnsConnectorImpl])
trait ReturnsConnector {

  def submit(cgtReference: CgtReference, submitReturnRequest: DesSubmitReturnRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def displayReturn(cgtReference: CgtReference, submissionId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class ReturnsConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends ReturnsConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("returns")

  override def submit(
    cgtReference: CgtReference,
    submitReturnRequest: DesSubmitReturnRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {
    val returnUrl: String = s"$baseUrl/capital-gains-tax/cgt-reference/${cgtReference.value}/return"

    EitherT[Future, Error, HttpResponse](
      http
        .post(returnUrl, Json.toJson(submitReturnRequest), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val url: String = s"$baseUrl/capital-gains-tax/returns/${cgtReference.value}"
    val queryParameters = Map(
      "fromDate" -> fromDate.format(dateFormatter),
      "toDate"   -> toDate.format(dateFormatter)
    )

    EitherT[Future, Error, HttpResponse](
      http
        .get(url, queryParameters, headers)(
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

  def displayReturn(cgtReference: CgtReference, submissionId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val url: String = s"$baseUrl/capital-gains-tax/${cgtReference.value}/$submissionId/return"

    EitherT[Future, Error, HttpResponse](
      http
        .get(url, headers       = headers)(
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

  private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ISO_DATE

}
