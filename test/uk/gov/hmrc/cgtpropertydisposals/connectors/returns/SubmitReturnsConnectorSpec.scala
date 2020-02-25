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

package uk.gov.hmrc.cgtpropertydisposals.connectors.returns

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsValue, Json}
import play.api.test.Helpers.{await, _}
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnectorImpl.DesSubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.PPDReturnDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.NumberOfProperties.One
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class SubmitReturnsConnectorSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

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

  val submitReturnRequest: SubmitReturnRequest = {
    sample[SubmitReturnRequest].copy(completeReturn = sample[CompleteReturn]
      .copy(triageAnswers = sample[CompleteSingleDisposalTriageAnswers].copy(numberOfProperties = One))
    )
  }

  val connector = new SubmitReturnsConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "SubmitReturnsConnectorImpl" when {

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders = Map("Authorization" -> s"Bearer $desBearerToken")//, "Environment" -> desEnvironment)

    def expectedSubmitReturnUrl(cgtReference: String) =
      s"""http://localhost:7022/capital-gains-tax/cgt-reference/$cgtReference/return"""

    "handling request to submit return" must {

      "do a post http call and pass correct parameters" in {
        for( a <- 1 to 10){
          val submitReturnRequest: SubmitReturnRequest = sample[SubmitReturnRequest]
          val ppdReturnDetails = PPDReturnDetails(submitReturnRequest)
          val desSubmitReturnRequest = DesSubmitReturnRequest(ppdReturnDetails)

          desSubmitReturnRequest
        }
      }

      "do a post http call and get the result" in {
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
            mockPost(
              expectedSubmitReturnUrl(submitReturnRequest.subscribedDetails.cgtReference.value),
              expectedHeaders,
              *
            )(
              Some(httpResponse)
            )

            await(connector.submit(submitReturnRequest).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error" when {

        "the call fails" in {
          mockPost(
            expectedSubmitReturnUrl(submitReturnRequest.subscribedDetails.cgtReference.value),
            expectedHeaders,
            *
          )(None)

          await(connector.submit(submitReturnRequest).value).isLeft shouldBe true
        }
      }

    }
  }
}
