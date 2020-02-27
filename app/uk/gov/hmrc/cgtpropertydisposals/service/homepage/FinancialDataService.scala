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

package uk.gov.hmrc.cgtpropertydisposals.service.homepage

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.connectors.homepage.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.cgtpropertydisposals.models.des.homepage._
import play.api.http.Status.OK
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FinancialDataServiceImpl])
trait FinancialDataService {

  def getFinancialData(financialData: FinancialDataRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, FinancialDataResponse]

}

@Singleton
class FinancialDataServiceImpl @Inject() (
  financialDataConnector: FinancialDataConnector,
  metrics: Metrics
)(
  implicit ec: ExecutionContext
) extends FinancialDataService
    with Logging {

  override def getFinancialData(
    financialData: FinancialDataRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, FinancialDataResponse] = {
    val timer = metrics.financialDataTimer.time()

    financialDataConnector.getFinancialData(financialData).subflatMap { response =>
      timer.close()
      if (response.status === OK) {
        response
          .parseJSON[DesFinancialDataResponse]()
          .map(prepareFinancialDataResponse(_))
          .leftMap(Error(_))
      } else {
        metrics.financialDataErrorCounter.inc()
        Left(Error(s"call to get financial data came back with status ${response.status}"))
      }
    }
  }

  def prepareFinancialDataResponse(desFinancialDataResponse: DesFinancialDataResponse): FinancialDataResponse =
    FinancialDataResponse(
      financialTransactions = desFinancialDataResponse.financialTransactions.map { t =>
        FinancialTransaction(AmountInPence.fromPounds(t.outstandingAmount))
      }
    )

}
