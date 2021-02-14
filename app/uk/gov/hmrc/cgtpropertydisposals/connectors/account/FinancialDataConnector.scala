/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.connectors.account

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpReads, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.http.HttpReads.Implicits._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FinancialDataConnectorImpl])
trait FinancialDataConnector {

  def getFinancialData(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class FinancialDataConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends FinancialDataConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("returns")

  def financialDataUrl(cgtReference: CgtReference): String =
    s"$baseUrl/enterprise/financial-data/ZCGT/${cgtReference.value}/CGT"

  override def getFinancialData(
    cgtReference: CgtReference,
    fromDate: LocalDate,
    toDate: LocalDate
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {

    val from = fromDate.format(DateTimeFormatter.ISO_DATE)
    val to   = toDate.format(DateTimeFormatter.ISO_DATE)

    val fdUrl       = financialDataUrl(cgtReference)
    val queryParams = Seq("dateFrom" -> from, "dateTo" -> to)

    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](fdUrl, queryParams, headers)(
          HttpReads[HttpResponse],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

  }

}
