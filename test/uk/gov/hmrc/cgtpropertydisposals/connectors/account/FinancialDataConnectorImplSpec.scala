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

package uk.gov.hmrc.cgtpropertydisposals.connectors.account

import java.time.LocalDate
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import play.api.Configuration
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class FinancialDataConnectorImplSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport {

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

  val connector = new FinancialDataConnectorImpl(mockHttp, new ServicesConfig(config))

  def queryParams(fromDate: LocalDate, toDate: LocalDate): Seq[(String, String)] =
    Seq("dateFrom" -> fromDate.toString, "dateTo" -> toDate.toString)

  val cgtReference                                                               = sample[CgtReference]
  val (fromDate, toDate)                                                         = LocalDate.of(2020, 1, 31) -> LocalDate.of(2020, 11, 2)

  private val emptyJsonBody = "{}"

  "FinancialDataConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    def expectedFinancialDataUrl(cgtReference: CgtReference): String =
      s"http://localhost:7022/enterprise/financial-data/ZCGT/${cgtReference.value}/CGT"

    "handling request to get financial data" must {

      "do a GET http call and get the result" in {

        List(
          HttpResponse(200, emptyJsonBody),
          HttpResponse(400, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(403, emptyJsonBody),
          HttpResponse(500, emptyJsonBody),
          HttpResponse(502, emptyJsonBody),
          HttpResponse(503, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGetWithQueryWithHeaders(
              expectedFinancialDataUrl(cgtReference),
              queryParams(fromDate, toDate),
              expectedHeaders
            )(
              Some(httpResponse)
            )

            await(connector.getFinancialData(cgtReference, fromDate, toDate).value) shouldBe Right(httpResponse)
          }
        }

      }

    }

    "return an error" when {

      "the call fails" in {
        mockGetWithQueryWithHeaders(
          expectedFinancialDataUrl(cgtReference),
          queryParams(fromDate, toDate),
          expectedHeaders
        )(None)

        await(connector.getFinancialData(cgtReference, fromDate, toDate).value).isLeft shouldBe true
      }
    }

  }

}
