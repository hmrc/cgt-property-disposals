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

package uk.gov.hmrc.cgtpropertydisposals.connectors.returns

import com.typesafe.config.ConfigFactory
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.{await, _}
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesSubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.generators.DesReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.given

import java.time.LocalDate

class ReturnsConnectorSpec
    extends AnyWordSpec
    with Matchers
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

  val connector: ReturnsConnector = app.injector.instanceOf[ReturnsConnector]

  private val emptyJsonBody = "{}"

  "SubmitReturnsConnectorImpl" when {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    "handling request to submit return" must {
      def expectedSubmitReturnUrl(cgtReference: String) =
        s"""/capital-gains-tax/cgt-reference/$cgtReference/return"""

      "handling request to submit return" must {
        "do a post http call and get the result" in {
          val cgtReference        = sample[CgtReference]
          val submitReturnRequest = sample[DesSubmitReturnRequest]

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
                POST,
                expectedSubmitReturnUrl(cgtReference.value),
                headers = expectedHeaders.toMap,
                body = Some(Json.toJson(submitReturnRequest).toString())
              ).thenReturn(httpResponse.status, httpResponse.body)

              val response = await(connector.submit(cgtReference, submitReturnRequest).value).value
              response.status shouldBe httpResponse.status
              response.body   shouldBe httpResponse.body
            }
          }
        }

        "return an error" when {
          "the call fails" in {
            val cgtReference        = sample[CgtReference]
            val submitReturnRequest = sample[DesSubmitReturnRequest]

            wireMockServer.stop()
            when(
              POST,
              expectedSubmitReturnUrl(cgtReference.value),
              headers = expectedHeaders.toMap,
              body = Some(Json.toJson(submitReturnRequest).toString())
            )

            await(connector.submit(cgtReference, submitReturnRequest).value).isLeft shouldBe true
            wireMockServer.start()
          }
        }
      }

      "handling requests to list returns" must {
        def expectedUrl(cgtReference: CgtReference) =
          s"/capital-gains-tax/returns/${cgtReference.value}"

        val cgtReference            = sample[CgtReference]
        val (fromDate, toDate)      = LocalDate.of(2000, 1, 2) -> LocalDate.of(2000, 2, 2)
        val expectedQueryParameters = Seq("fromDate" -> "2000-01-02", "toDate" -> "2000-02-02")

        "do a get request and return the response" in {
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

              when(GET, expectedUrl(cgtReference), expectedQueryParameters.toMap, expectedHeaders.toMap)
                .thenReturn(httpResponse.status, httpResponse.body)

              val response = await(connector.listReturns(cgtReference, fromDate, toDate).value).value
              response.status shouldBe httpResponse.status
              response.body   shouldBe httpResponse.body
            }
          }
        }

        "return an error" when {
          "the call fails" in {
            wireMockServer.stop()
            when(GET, expectedUrl(cgtReference), expectedQueryParameters.toMap, expectedHeaders.toMap)
            await(connector.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
            wireMockServer.start()
          }
        }
      }

      "handling requests to get a returns" must {
        def expectedUrl(cgtReference: CgtReference, submissionId: String) =
          s"/capital-gains-tax/${cgtReference.value}/$submissionId/return"

        val cgtReference = sample[CgtReference]
        val submissionId = "id"

        "do a get request and return the response" in {
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

              when(GET, expectedUrl(cgtReference, submissionId), Map.empty, expectedHeaders.toMap)
                .thenReturn(httpResponse.status, httpResponse.body)

              val response = await(connector.displayReturn(cgtReference, submissionId).value).value
              response.status shouldBe httpResponse.status
              response.body   shouldBe httpResponse.body
            }
          }
        }

        "return an error" when {
          "the call fails" in {
            wireMockServer.stop()
            when(GET, expectedUrl(cgtReference, submissionId), Map.empty, expectedHeaders.toMap)
            await(connector.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
            wireMockServer.start()
          }
        }
      }
    }
  }
}
