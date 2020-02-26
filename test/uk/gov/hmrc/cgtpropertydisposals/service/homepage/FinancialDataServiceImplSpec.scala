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

import java.time.LocalDateTime

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.homepage.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.homepage._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val financialDataConnector = mock[FinancialDataConnector]
  val financialDataService   = new FinancialDataServiceImpl(financialDataConnector, MockMetrics.metrics)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val today = LocalDateTime.now()

  private def financialDataResponse(
    idType: String,
    idNumber: String,
    regimeType: String,
    outstandingAmount: Double
  ): FinancialDataResponse = {
    val financialTransactions = List(
      FinancialTransaction(outstandingAmount = BigDecimal(outstandingAmount))
    )
    FinancialDataResponse(
      idType                = idType,
      idNumber              = idNumber,
      regimeType            = regimeType,
      processingDate        = today,
      financialTransactions = financialTransactions
    )
  }

  def mockGetFinancialData(financialData: FinancialDataRequest)(response: Either[Error, HttpResponse]) =
    (financialDataConnector
      .getFinancialData(_: FinancialDataRequest)(_: HeaderCarrier))
      .expects(financialData, *)
      .returning(EitherT.fromEither[Future](response))

  def jsonBody(financialDataRequest: FinancialDataRequest, outstandingAmount: Double): String =
    s"""
       |{
       |   "idType": "${financialDataRequest.idType}",
       |   "idNumber": "${financialDataRequest.idNumber}",
       |    "regimeType": "${financialDataRequest.regimeType}",
       |    "processingDate": "$today",
       |    "financialTransactions": [{
       |        "outstandingAmount":$outstandingAmount
       |    }]
       |}
       |""".stripMargin

  val financialDataRequest = sample[FinancialDataRequest]

  "FinancialDataService" when {

    "handling get financial data" should {

      "handle successful results" when {

        "there is a total left to pay" in {

          val fdResponse = financialDataResponse(
            financialDataRequest.idType,
            financialDataRequest.idNumber,
            financialDataRequest.regimeType,
            30000d
          )
          mockGetFinancialData(financialDataRequest)(
            Right(HttpResponse(200, Some(Json.parse(jsonBody(financialDataRequest, 30000)))))
          )

          await(financialDataService.getFinancialData(financialDataRequest).value) shouldBe Right(fdResponse)
        }

        "there is no total left to pay" in {

          val financialDataRequest = sample[FinancialDataRequest]
          val fdResponse = financialDataResponse(
            financialDataRequest.idType,
            financialDataRequest.idNumber,
            financialDataRequest.regimeType,
            0d
          )
          mockGetFinancialData(financialDataRequest)(
            Right(HttpResponse(200, Some(Json.parse(jsonBody(financialDataRequest, 0)))))
          )

          await(financialDataService.getFinancialData(financialDataRequest).value) shouldBe Right(fdResponse)
        }

      }

      "return an error" when {

        "the http call comes back with a status other than 200" in {
          mockGetFinancialData(financialDataRequest)(Right(HttpResponse(500)))
          await(financialDataService.getFinancialData(financialDataRequest).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockGetFinancialData(financialDataRequest)(Right(HttpResponse(200)))
          await(financialDataService.getFinancialData(financialDataRequest).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockGetFinancialData(financialDataRequest)(Right(HttpResponse(200, Some(JsNumber(1)))))
          await(financialDataService.getFinancialData(financialDataRequest).value).isLeft shouldBe true
        }

      }
    }
  }

}
