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

package uk.gov.hmrc.cgtpropertydisposals.connectors.homepage

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.homepage.FinancialDataRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FinancialDataConnectorImpl])
trait FinancialDataConnector {

  def getFinancialData(financialData: FinancialDataRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class FinancialDataConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends FinancialDataConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("returns")

  def financialDataUrl(f: FinancialDataRequest): String =
    s"$baseUrl/enterprise/financial-data/${f.idType}/${f.idNumber}/${f.regimeType}"

  override def getFinancialData(
    financialData: FinancialDataRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {

    val fdUrl       = financialDataUrl(financialData)
    val queryParams = Map("dateFrom" -> financialData.fromDate.toString, "dateTo" -> financialData.toDate.toString)

    EitherT[Future, Error, HttpResponse](
      http
        .get(fdUrl, queryParams, headers)
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )

  }

}
