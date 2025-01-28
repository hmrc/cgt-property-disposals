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

import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import java.time.LocalDate

class FinancialDataConnectorImplSpec
    extends AnyWordSpec
    with Matchers
    with IdiomaticMockito
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite
    with EitherValues {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config: Configuration = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      returns {
         |        port     = $wireMockPort
         |    }
         |  }
         |}
         |
         |des {
         |  bearer-token = $desBearerToken
         |  environment  = $desEnvironment
         |}
         |create-internal-auth-token-on-start = false
         |""".stripMargin
    )
  )

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: FinancialDataConnector = app.injector.instanceOf[FinancialDataConnector]

  def queryParams(fromDate: LocalDate, toDate: LocalDate): Seq[(String, String)] =
    Seq("dateFrom" -> fromDate.toString, "dateTo" -> toDate.toString)

  private val cgtReference                                                       = sample[CgtReference]
  val (fromDate, toDate)                                                         = LocalDate.of(2020, 1, 31) -> LocalDate.of(2020, 11, 2)

  private val emptyJsonBody = "{}"

  "FinancialDataConnectorImpl" when {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    "handling request to get financial data" must {
      "do a GET http call and get the result" in {
        List(
          HttpResponse(200, emptyJsonBody),
          HttpResponse(400, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(403, emptyJsonBody),
          HttpResponse(404, emptyJsonBody),
          HttpResponse(500, emptyJsonBody),
          HttpResponse(502, emptyJsonBody),
          HttpResponse(503, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              GET,
              s"/enterprise/financial-data/ZCGT/${cgtReference.value}/CGT",
              queryParams(fromDate, toDate).toMap,
              expectedHeaders.toMap
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.getFinancialData(cgtReference, fromDate, toDate).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }
    }

    "return an error" when {
      "the call fails" in {
        wireMockServer.stop()

        when(
          GET,
          s"/enterprise/financial-data/ZCGT/${cgtReference.value}/CGT",
          queryParams(fromDate, toDate).toMap,
          expectedHeaders.toMap
        )

        await(connector.getFinancialData(cgtReference, fromDate, toDate).value).isLeft shouldBe true
        wireMockServer.start()
      }
    }
  }
}
