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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionDetails
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class EmailConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val (accountCreatedTemplateId, accountCreatedSignInUrl) =
    "template" -> "sign-in"

  val config = Configuration(
    ConfigFactory.parseString(
      s"""
        |microservice {
        |  services {
        |    email {
        |      protocol = http
        |      host     = host
        |      port     = 123
        |    }
        |  }
        |}
        |
        |email {
        |    account-created {
        |        template-id = "$accountCreatedTemplateId"
        |        sign-in-url = "$accountCreatedSignInUrl"
        |    }
        |}
        |""".stripMargin
    )
  )

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector = new EmailConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)))

  "EmailConnectorImpl" when {

    "it receives a request to send an account created confirmation email" must {

      val cgtReference = sample[CgtReference]

      val subscriptionDetails = sample[SubscriptionDetails]

      val expectedRequestBody = Json.parse(
        s"""{
           |  "to": ["${subscriptionDetails.emailAddress.value}"],
           |  "templateId": "$accountCreatedTemplateId",
           |  "parameters": {
           |    "name": "${subscriptionDetails.contactName.value}",
           |    "cgtReference": "${cgtReference.value}"
           |  },
           |  "force": false
           |}
           |""".stripMargin
      )

      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401),
          HttpResponse(400)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {

            mockPost(
              s"http://host:123/hmrc/email",
              Map.empty,
              expectedRequestBody
            )(Some(httpResponse))

            await(connector.sendSubscriptionConfirmationEmail(subscriptionDetails, cgtReference).value) shouldBe Right(
              httpResponse
            )
          }
        }
      }

      "return an error" when {

        "the call fails" in {
          mockPost(
            s"http://host:123/hmrc/email",
            Map.empty,
            expectedRequestBody
          )(None)

          await(connector.sendSubscriptionConfirmationEmail(subscriptionDetails, cgtReference).value).isLeft shouldBe true
        }
      }
    }
  }
}
