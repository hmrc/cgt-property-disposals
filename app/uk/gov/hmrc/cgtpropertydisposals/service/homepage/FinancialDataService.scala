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

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.{NOT_FOUND, OK}
import uk.gov.hmrc.cgtpropertydisposals.connectors.account.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.des.{DesErrorResponse, DesFinancialDataResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesErrorResponse.SingleDesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.models.finance.{AmountInPence, FinancialTransaction}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.finance.FinancialTransaction.Payment
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[FinancialDataServiceImpl])
trait FinancialDataService {

  def getFinancialData(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, List[FinancialTransaction]]

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
    cgtReference: CgtReference,
    fromDate: LocalDate,
    toDate: LocalDate
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, List[FinancialTransaction]] = {
    val timer = metrics.financialDataTimer.time()

    financialDataConnector.getFinancialData(cgtReference, fromDate, toDate).subflatMap { response =>
      timer.close()
      if (response.status === OK) {
        response
          .parseJSON[DesFinancialDataResponse]()
          .map(prepareFinancialDataResponse(_))
          .leftMap(Error(_))
      } else if (isNoReturnsResponse(response)) {
        Right(List.empty)
      } else {
        metrics.financialDataErrorCounter.inc()
        Left(Error(s"call to get financial data came back with status ${response.status}"))
      }
    }
  }

  private def isNoReturnsResponse(response: HttpResponse): Boolean = {
    def isNoReturnResponse(e: SingleDesErrorResponse) =
      e.code === "NOT_FOUND" &&
        e.reason === "The remote endpoint has indicated that no data can be found."

    lazy val hasNoReturnBody = response
      .parseJSON[DesErrorResponse]()
      .bimap(
        _ => false,
        _.fold(isNoReturnResponse, _.failures.exists(isNoReturnResponse))
      )
      .merge

    response.status === NOT_FOUND && hasNoReturnBody
  }

  def prepareFinancialDataResponse(desFinancialDataResponse: DesFinancialDataResponse): List[FinancialTransaction] =
    desFinancialDataResponse.financialTransactions.map { t =>
      FinancialTransaction(
        t.chargeReference,
        AmountInPence.fromPounds(t.originalAmount),
        AmountInPence.fromPounds(t.outstandingAmount),
        t.items.fold(List.empty[Payment])(
          _.map(i =>
            Payment(
              AmountInPence.fromPounds(i.amount),
              i.clearingDate
            )
          )
        )
      )
    }

}
