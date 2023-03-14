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
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Email
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.ContactName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.ExecutionContext.Implicits.global

class EmailConnectorImplSpec extends AnyWordSpec with Matchers with MockFactory with HttpSupport {

  val (accountCreatedTemplateId, accountCreatedSignInUrl, returnSubmittedTemplateId) =
    ("template", "sign-in", "template-return-submitted")

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
         |    return-submitted {
         |        template-id = "$returnSubmittedTemplateId"
         |    }
         |}
         |""".stripMargin
    )
  )

  private val emptyJsonBody = "{}"

  val connector =
    new EmailConnectorImpl(mockHttp, new ServicesConfig(config))

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
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {

            mockPost(
              s"http://host:123/hmrc/email",
              Seq.empty,
              expectedRequestBody
            )(Some(httpResponse))

            await(connector.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value) shouldBe Right(
              httpResponse
            )
          }
        }
      }

      "return an error" when {

        "the call fails" in {
          mockPost(
            s"http://host:123/hmrc/email",
            Seq.empty,
            expectedRequestBody
          )(None)

          await(
            connector.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value
          ).isLeft shouldBe true
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
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {

            mockPost(
              s"http://host:123/hmrc/email",
              Seq.empty,
              expectedRequestBody
            )(Some(httpResponse))

            await(connector.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value) shouldBe Right(
              httpResponse
            )
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
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {

            mockPost(
              s"http://host:123/hmrc/email",
              Seq.empty,
              expectedRequestBody
            )(Some(httpResponse))

            await(
              connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value
            ) shouldBe Right(
              httpResponse
            )
          }
        }
      }

      "return an error" when {

        "the call fails" in {
          mockPost(
            s"http://host:123/hmrc/email",
            Seq.empty,
            expectedRequestBody
          )(None)

          await(
            connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value
          ).isLeft shouldBe true
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
          HttpResponse(204, emptyJsonBody),
          HttpResponse(401, emptyJsonBody),
          HttpResponse(400, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {

            mockPost(
              s"http://host:123/hmrc/email",
              Seq.empty,
              expectedRequestBody
            )(Some(httpResponse))

            await(
              connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value
            ) shouldBe Right(
              httpResponse
            )
          }
        }
      }
    }
  }
}
