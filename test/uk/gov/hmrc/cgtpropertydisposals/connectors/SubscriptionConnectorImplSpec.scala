/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.models.{SubscriptionDetails, sample}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  val config = Configuration(
    ConfigFactory.parseString(
      s"""
       |microservice {
       |  services {
       |      subscription {
       |      protocol = http
       |      host     = host
       |      port     = 123
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

  val connector = new SubscriptionConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "SubscriptionConnectorImpl" when {

    "handling request to subscribe" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val expectedHeaders            = Map("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)
      val subscriptionDetails        = sample[SubscriptionDetails]

      "do a post http call and return the result" in {
        List(
          HttpResponse(200),
          HttpResponse(200, Some(JsString("hi"))),
          HttpResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPost(s"http://host:123/subscribe/individual", expectedHeaders, Json.toJson(subscriptionDetails))(
              Some(httpResponse)
            )

            await(connector.subscribe(subscriptionDetails).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockPost(s"http://host:123/subscribe/individual", expectedHeaders, Json.toJson(subscriptionDetails))(None)

        await(connector.subscribe(subscriptionDetails).value).isLeft shouldBe true
      }

    }
  }

}
