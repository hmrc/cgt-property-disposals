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

import java.time.LocalDate

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.test.Helpers.{await, _}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesSubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class ReturnsConnectorSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport {

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

  val connector = new ReturnsConnectorImpl(mockHttp, new ServicesConfig(config))

  private val emptyJsonBody = "{}"

  "SubmitReturnsConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    "handling request to submit return" must {

      def expectedSubmitReturnUrl(cgtReference: String) =
        s"""http://localhost:7022/capital-gains-tax/cgt-reference/$cgtReference/return"""

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
              mockPost(
                expectedSubmitReturnUrl(cgtReference.value),
                expectedHeaders,
                *
              )(
                Some(httpResponse)
              )

              await(connector.submit(cgtReference, submitReturnRequest).value) shouldBe Right(httpResponse)
            }
          }
        }

        "return an error" when {

          "the call fails" in {
            val cgtReference        = sample[CgtReference]
            val submitReturnRequest = sample[DesSubmitReturnRequest]

            mockPost(
              expectedSubmitReturnUrl(cgtReference.value),
              expectedHeaders,
              *
            )(None)

            await(connector.submit(cgtReference, submitReturnRequest).value).isLeft shouldBe true
          }
        }

      }

      "handling requests to list returns" must {

        def expectedUrl(cgtReference: CgtReference) =
          s"http://localhost:7022/capital-gains-tax/returns/${cgtReference.value}"

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

              mockGetWithQueryWithHeaders(expectedUrl(cgtReference), expectedQueryParameters, expectedHeaders)(
                Some(httpResponse)
              )
              await(connector.listReturns(cgtReference, fromDate, toDate).value) shouldBe Right(httpResponse)
            }
          }

        }

        "return an error" when {

          "the call fails" in {
            mockGetWithQueryWithHeaders(expectedUrl(cgtReference), expectedQueryParameters, expectedHeaders)(None)
            await(connector.listReturns(cgtReference, fromDate, toDate).value).isLeft shouldBe true
          }

        }

      }

      "handling requests to get a returns" must {

        def expectedUrl(cgtReference: CgtReference, submissionId: String) =
          s"http://localhost:7022/capital-gains-tax/${cgtReference.value}/$submissionId/return"

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
              mockGetWithQueryWithHeaders(expectedUrl(cgtReference, submissionId), Seq.empty, expectedHeaders)(
                Some(httpResponse)
              )
              await(connector.displayReturn(cgtReference, submissionId).value) shouldBe Right(httpResponse)
            }
          }

        }

        "return an error" when {

          "the call fails" in {
            mockGetWithQueryWithHeaders(expectedUrl(cgtReference, submissionId), Seq.empty, expectedHeaders)(None)
            await(connector.displayReturn(cgtReference, submissionId).value).isLeft shouldBe true
          }

        }

      }

    }

  }

}
