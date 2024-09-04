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

package uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments

import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class EnrolmentStoreProxyConnectorImplSpec
    extends AnyWordSpec
    with Matchers
    with IdiomaticMockito
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite
    with EitherValues {

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
        |microservice {
        |  services {
        |    enrolment-store-proxy {
        |      protocol = http
        |      host     = $wireMockHost
        |      port     = $wireMockPort
        |    }
        |  }
        |}
        |""".stripMargin
    )
  )

  implicit val hc: HeaderCarrier              = HeaderCarrier()
  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: EnrolmentStoreProxyConnector = app.injector.instanceOf[EnrolmentStoreProxyConnector]

  "EnrolmentStoreProxyConnectorImpl" when {
    "handling requests to get all principal enrolments for a cgt reference" must {
      val emptyJsonBody = "{}"

      val expectedQueryParameters = List("type" -> "principal")

      "do a GET http call and get the result" in {
        val cgtReference = sample[CgtReference]

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
            when(
              GET,
              s"/enrolment-store-proxy/enrolment-store/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}/users",
              expectedQueryParameters.toMap,
              Map.empty
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.getPrincipalEnrolments(cgtReference).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }
    }
  }
}
