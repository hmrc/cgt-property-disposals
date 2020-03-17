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

import java.time.LocalDate

import com.google.inject.Inject
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.i18n.{DefaultLangs, Lang, Langs, Messages, MessagesApi, MessagesImpl, MessagesProvider}
import play.api.libs.json.Json
import play.api.test.Helpers
import play.api.test.Helpers._
import play.api.{Configuration, Mode}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.LocalDateUtils
import uk.gov.hmrc.cgtpropertydisposals.models.finance.{AmountInPence, Charge, ChargeType, MoneyUtils}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse.ReturnCharge
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}

import scala.concurrent.ExecutionContext.Implicits.global

class EmailConnectorImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {
  val messagesKeys = Map()
  val monthNames: Map[String, Map[String, String]] = Map(
    "en-GB" -> Map(
      "date.1"  -> "January",
      "date.2"  -> "February",
      "date.3"  -> "March",
      "date.4"  -> "April",
      "date.5"  -> "May",
      "date.6"  -> "June",
      "date.7"  -> "July",
      "date.8"  -> "August",
      "date.9"  -> "September",
      "date.10" -> "October",
      "date.11" -> "November",
      "date.12" -> "December"
    )
  )
  implicit val messagesApi                        = Helpers.stubMessagesApi(monthNames)
  implicit val lang: Lang                         = Lang.apply("en-GB")
  implicit val messagesProvider: MessagesProvider = MessagesImpl(lang, messagesApi)

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

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val connector =
    new EmailConnectorImpl(mockHttp, new ServicesConfig(config, new RunMode(config, Mode.Test)), messagesApi)

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

    "it receives a request to send a return submitted confirmation email with SOME charge" must {

      val taxDue               = AmountInPence(50)
      val chargeRef            = "CHARGE_REF"
      val submissionId         = "submissionId"
      val dueDate              = LocalDate.of(2020, 7, 1)
      val charge               = ReturnCharge(chargeRef, taxDue, dueDate)
      val submitReturnResponse = sample[SubmitReturnResponse].copy(formBundleId = submissionId, charge = Some(charge))
      val subscribedDetails    = sample[SubscribedDetails]
      val expectedRequestBody = Json.parse(
        s"""{
           |  "to": ["${subscribedDetails.emailAddress.value}"],
           |  "templateId": "$returnSubmittedTemplateId",
           |  "parameters": {
           |    "name": "${subscribedDetails.contactName.value}",
           |    "submissionId": "$submissionId",
           |    "taxDue": "${MoneyUtils.formatAmountOfMoneyWithPoundSign(taxDue.value)}",
           |    "chargeRef": "$chargeRef",
           |    "dueDate": "1 July 2020"
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

            await(connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value) shouldBe Right(
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

          await(connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value).isLeft shouldBe true
        }
      }
    }
    "it receives a request to send a return submitted confirmation email with NO charge" must {

      val taxDue               = AmountInPence(0)
      val submissionId         = "submissionId"
      val submitReturnResponse = sample[SubmitReturnResponse].copy(formBundleId = submissionId, charge = None)
      val subscribedDetails    = sample[SubscribedDetails]
      val expectedRequestBody = Json.parse(
        s"""{
           |  "to": ["${subscribedDetails.emailAddress.value}"],
           |  "templateId": "$returnSubmittedTemplateId",
           |  "parameters": {
           |    "name": "${subscribedDetails.contactName.value}",
           |    "submissionId": "$submissionId",
           |    "taxDue": "${MoneyUtils.formatAmountOfMoneyWithPoundSign(taxDue.value)}"
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

            await(connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value) shouldBe Right(
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

          await(connector.sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails).value).isLeft shouldBe true
        }
      }
    }
  }
}
