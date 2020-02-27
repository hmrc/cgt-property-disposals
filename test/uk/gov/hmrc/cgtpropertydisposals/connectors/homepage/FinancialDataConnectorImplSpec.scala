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

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.des.homepage.FinancialDataRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class FinancialDataConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      returns {
         |        protocol = http
         |        host     = localhost
         |        port     = 7022
         |    }
         |  }
         |}
         |
         |des {
         |  bearer-token = $desBearerToken
         |  environment  = $desEnvironment
         |}
         |""".stripMargin
    )
  )

  val financialDataRequest: FinancialDataRequest = sample[FinancialDataRequest]

  val connector = new FinancialDataConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  def queryParams(financialData: FinancialDataRequest): Map[String, String] =
    Map("dateFrom" -> financialData.fromDate.toString, "dateTo" -> financialData.toDate.toString)

  "FinancialDataConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Map("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    def expectedFinancialDataUrl(f: FinancialDataRequest): String =
      s"http://localhost:7022/enterprise/financial-data/ZCGT/${f.idNumber}/CGT"

    "handling request to get financial data" must {

      "do a GET http call and get the result" in {

        List(
          HttpResponse(200),
          HttpResponse(400),
          HttpResponse(401),
          HttpResponse(403),
          HttpResponse(500),
          HttpResponse(502),
          HttpResponse(503)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGet(
              expectedFinancialDataUrl(financialDataRequest),
              queryParams(financialDataRequest),
              expectedHeaders
            )(
              Some(httpResponse)
            )

            await(connector.getFinancialData(financialDataRequest).value) shouldBe Right(httpResponse)
          }
        }

      }

    }

    "return an error" when {

      "the call fails" in {
        mockGet(
          expectedFinancialDataUrl(financialDataRequest),
          queryParams(financialDataRequest),
          expectedHeaders
        )(None)

        await(connector.getFinancialData(financialDataRequest).value).isLeft shouldBe true
      }
    }

  }

}
