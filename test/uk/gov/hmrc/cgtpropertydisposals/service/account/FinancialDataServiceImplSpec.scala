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

package uk.gov.hmrc.cgtpropertydisposals.service.account

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime}

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.account.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.finance.FinancialTransaction
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val financialDataConnector = mock[FinancialDataConnector]
  val financialDataService   = new FinancialDataServiceImpl(financialDataConnector, MockMetrics.metrics)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val today = LocalDateTime.now()

  def mockGetFinancialData(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    response: Either[Error, HttpResponse]
  ) =
    (financialDataConnector
      .getFinancialData(_: CgtReference, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(cgtReference, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  def jsonBody(transactions: List[FinancialTransaction]): String = {
    val transactionsJson = transactions
      .map { t =>
        val paymentsJson =
          if (t.payments.isEmpty) ""
          else
            t.payments.map(payment => s"""|{
          | "amount": ${payment.amount.inPounds()},
          | "clearingDate" : "${payment.clearingDate.format(DateTimeFormatter.ISO_DATE)}"
          |}""".stripMargin).mkString(", ")

        s"""
       |{
       | "chargeReference" : "${t.chargeReference}",
       | "originalAmount" : ${t.originalAmount.inPounds()},
       | "outstandingAmount" : ${t.outstandingAmount.inPounds()} ${if (paymentsJson.isEmpty) ""
           else
             s""", "items": [ $paymentsJson ]"""}
       |}""".stripMargin
      }
      .mkString(", ")

    s"""{ "financialTransactions": [ $transactionsJson ] }"""
  }

  val cgtReference       = sample[CgtReference]
  val (fromDate, toDate) = LocalDate.of(2020, 1, 31) -> LocalDate.of(2020, 11, 2)

  "FinancialDataService" when {

    "handling get financial data" should {

      "handle successful results" when {

        "the JSON can be parsed" in {
          val transactions = List.fill(3)(sample[FinancialTransaction])

          mockGetFinancialData(cgtReference, fromDate, toDate)(
            Right(HttpResponse(200, Some(Json.parse(jsonBody(transactions)))))
          )

          await(financialDataService.getFinancialData(cgtReference, fromDate, toDate).value) shouldBe Right(
            transactions
          )
        }

      }

      "return an error" when {

        "the http call comes back with a status other than 200" in {
          mockGetFinancialData(cgtReference, fromDate, toDate)(Right(HttpResponse(500)))
          await(financialDataService.getFinancialData(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockGetFinancialData(cgtReference, fromDate, toDate)(Right(HttpResponse(200)))
          await(financialDataService.getFinancialData(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockGetFinancialData(cgtReference, fromDate, toDate)(Right(HttpResponse(200, Some(JsNumber(1)))))
          await(financialDataService.getFinancialData(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        }

      }
    }
  }

}
