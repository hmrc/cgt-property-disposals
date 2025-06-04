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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import com.typesafe.config.ConfigFactory
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.test.Helpers.*
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.IdGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.OnboardingGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.SubmitReturnGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.ContactName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class EmailConnectorImplSpec
    extends AnyWordSpec
    with Matchers
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite
    with EitherValues {

  val (accountCreatedTemplateId, accountCreatedSignInUrl, returnSubmittedTemplateId) =
    ("template", "sign-in", "template-return-submitted")

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |    email {
         |      port     = $wireMockPort
         |    }
         |  }
         |}
         |
         |email {
         |    account-created {
         |        template-id = "$accountCreatedTemplateId"
         |        sign-in-url = "$accountCreatedSignInUrl"
         |    }
         |    return-submitted {
         |        template-id = "$returnSubmittedTemplateId"
         |    }
         |}
         |create-internal-auth-token-on-start = false
         |""".stripMargin
    )
  )

  private val emptyJsonBody = "{}"

  override def fakeApplication(): Application = new GuiceApplicationBuilder().configure(config).build()

  val connector: EmailConnector = app.injector.instanceOf[EmailConnector]

  "EmailConnectorImpl" when {
    "it receives a request to send an account created confirmation email in English" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().copy(otherHeaders = Seq("Accept-Language" -> "en"))

      val cgtReference = sample[CgtReference]
      val email        = Email("email@test.com")
      val contactName  = ContactName("name")

      val expectedRequestBody = Json.parse(
        s"""{
           |  "to": ["${email.value}"],
           |  "templateId": "$accountCreatedTemplateId",
           |  "parameters": {
           |    "name": "${contactName.value}",
           |    "cgtReference": "${cgtReference.value}"
           |  },
           |  "force": false
           |}
           |""".stripMargin
      )

      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {

            when(
              POST,
              s"/hmrc/email",
              body = Some(expectedRequestBody.toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response =
              await(connector.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error" when {
        "the call fails" in {
          wireMockServer.stop()
          when(POST, s"/hmrc/email", body = Some(expectedRequestBody.toString()))

          await(
            connector.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value
          ).isLeft shouldBe true
          wireMockServer.start()
        }
      }
    }

    "it receives a request to send an account created confirmation email in Welsh" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().copy(otherHeaders = Seq("accept-language" -> "CY"))

      val cgtReference = sample[CgtReference]
      val email        = Email("email@test.com")
      val contactName  = ContactName("name")

      val expectedRequestBody = Json.parse(
        s"""{
           |  "to": ["${email.value}"],
           |  "templateId": "${accountCreatedTemplateId + "_cy"}",
           |  "parameters": {
           |    "name": "${contactName.value}",
           |    "cgtReference": "${cgtReference.value}"
           |  },
           |  "force": false
           |}
           |""".stripMargin
      )

      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              POST,
              s"/hmrc/email",
              body = Some(expectedRequestBody.toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response =
              await(connector.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }
    }

    "it receives a request to send a return submitted confirmation email in English" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().copy(otherHeaders = Seq("Accept-Language" -> "en"))

      val submissionId         = "submissionId"
      val submitReturnResponse = sample[SubmitReturnResponse].copy(formBundleId = submissionId)
      val subscribedDetails    = sample[SubscribedDetails]
      val expectedRequestBody  = Json.parse(
        s"""{
           |  "to": ["${subscribedDetails.emailAddress.value}"],
           |  "templateId": "$returnSubmittedTemplateId",
           |  "parameters": {
           |    "name": "${subscribedDetails.contactName.value}",
           |    "submissionId": "$submissionId"
           |  },
           |  "force": false
           |}
           |""".stripMargin
      )

      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              POST,
              s"/hmrc/email",
              body = Some(expectedRequestBody.toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response =
              await(connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error" when {
        "the call fails" in {
          wireMockServer.stop()
          when(POST, s"/hmrc/email", body = Some(expectedRequestBody.toString()))

          await(
            connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value
          ).isLeft shouldBe true
          wireMockServer.start()
        }
      }
    }

    "it receives a request to send a return submitted confirmation email in Welsh" must {
      implicit val hc: HeaderCarrier = HeaderCarrier().copy(otherHeaders = Seq("Accept-Language" -> "cy"))

      val submissionId         = "submissionId"
      val submitReturnResponse = sample[SubmitReturnResponse].copy(formBundleId = submissionId)
      val subscribedDetails    = sample[SubscribedDetails]
      val expectedRequestBody  = Json.parse(
        s"""{
           |  "to": ["${subscribedDetails.emailAddress.value}"],
           |  "templateId": "${returnSubmittedTemplateId + "_cy"}",
           |  "parameters": {
           |    "name": "${subscribedDetails.contactName.value}",
           |    "submissionId": "$submissionId"
           |  },
           |  "force": false
           |}
           |""".stripMargin
      )

      "make a http put call and return a result" in {
        List(
          HttpResponse(204),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              POST,
              s"/hmrc/email",
              body = Some(expectedRequestBody.toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response =
              await(connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }
    }
  }
}
