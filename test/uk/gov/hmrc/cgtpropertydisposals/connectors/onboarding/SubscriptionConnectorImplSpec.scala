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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import com.typesafe.config.ConfigFactory
import org.mockito.IdiomaticMockito
import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.{Application, Configuration}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.util.WireMockMethods
import uk.gov.hmrc.http.test.WireMockSupport
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

class SubscriptionConnectorImplSpec
    extends AnyWordSpec
    with Matchers
    with IdiomaticMockito
    with WireMockSupport
    with WireMockMethods
    with GuiceOneAppPerSuite
    with EitherValues {

  val (desBearerToken, desEnvironment) = "token" -> "environment"

  private val config = Configuration(
    ConfigFactory.parseString(
      s"""
         |microservice {
         |  services {
         |      subscription {
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

  val connector: SubscriptionConnector = app.injector.instanceOf[SubscriptionConnector]

  private val emptyJsonBody = "{}"

  "SubscriptionConnectorImpl" when {
    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)

    "handling request to update subscription details" must {
      val cgtReference = sample[CgtReference]
      val request      = sample[DesSubscriptionUpdateRequest]

      val expectedResponse =
        """
          |{
          |    "regime": "CGT",
          |    "processingDate" : "2015-12-17T09:30:47Z",
          |    "formBundleNumber": "012345678901",
          |    "cgtReferenceNumber": "XXCGTP123456789",
          |    "countryCode": "GB",
          |    "postalCode" : "TF34NT"
          |}
          |""".stripMargin

      "do a put http call and return the result" in {
        List(
          HttpResponse(200, Json.parse(expectedResponse), Map.empty[String, Seq[String]]),
          HttpResponse(500, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              PUT,
              s"/subscriptions/CGT/ZCGT/${cgtReference.value}",
              body = Some(Json.toJson(request).toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.updateSubscription(request, cgtReference).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error when the future fails" in {
        wireMockServer.stop()
        when(PUT, s"/subscriptions/CGT/ZCGT/${cgtReference.value}", body = Some(Json.toJson(request).toString()))
        await(connector.updateSubscription(request, cgtReference).value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "handling request to get subscription display details" must {
      val cgtReference = CgtReference("XFCGT123456789")

      val expectedResponse =
        """
          |{
          |    "regime": "CGT",
          |    "subscriptionDetails": {
          |        "individual": {
          |            "typeOfPerson": "Individual",
          |            "firstName": "Luke",
          |            "lastName": "Bishop"
          |        },
          |        "isRegisteredWithId": true,
          |        "addressDetails": {
          |            "addressLine1": "100 Sutton Street",
          |            "addressLine2": "Wokingham",
          |            "addressLine3": "Surrey",
          |            "addressLine4": "London",
          |            "postalCode": "DH14EJ",
          |            "countryCode": "GB"
          |        },
          |        "contactDetails": {
          |            "contactName": "Stephen Wood",
          |            "phoneNumber": "(+013)32752856",
          |            "mobileNumber": "(+44)7782565326",
          |            "faxNumber": "01332754256",
          |            "emailAddress": "stephen@abc.co.uk"
          |        }
          |    }
          |}
          |""".stripMargin

      "do a get http call and return the result" in {
        List(
          HttpResponse(200, Json.parse(expectedResponse), Map.empty[String, Seq[String]]),
          HttpResponse(500, emptyJsonBody)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              GET,
              s"/subscriptions/CGT/ZCGT/${cgtReference.value}",
              headers = expectedHeaders.toMap
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.getSubscription(cgtReference).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error when the future fails" in {
        wireMockServer.stop()
        when(GET, s"/subscriptions/CGT/ZCGT/${cgtReference.value}", headers = expectedHeaders.toMap)
        await(connector.getSubscription(cgtReference).value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "handling request to subscribe" must {
      val request = sample[DesSubscriptionRequest]

      "do a post http call and return the result" in {
        List(
          HttpResponse(200, "{}"),
          HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
          HttpResponse(500, "{}")
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(
              POST,
              "/subscriptions/create/CGT",
              headers = expectedHeaders.toMap,
              body = Some(Json.toJson(request).toString())
            ).thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.subscribe(request).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error when the future fails" in {
        wireMockServer.stop()
        when(
          POST,
          "/subscriptions/create/CGT",
          headers = expectedHeaders.toMap,
          body = Some(Json.toJson(request).toString())
        )

        await(connector.subscribe(request).value).isLeft shouldBe true
        wireMockServer.start()
      }
    }

    "handling requests to get subscription status" must {
      val sapNumber                     = SapNumber("sap")
      val expectedSubscriptionStatusUrl = s"/cross-regime/subscription/CGT/${sapNumber.value}/status"

      "do a post http call and return the result" in {
        List(
          HttpResponse(200, "{}"),
          HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
          HttpResponse(500, "{}")
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            when(GET, expectedSubscriptionStatusUrl, headers = expectedHeaders.toMap)
              .thenReturn(httpResponse.status, httpResponse.body)

            val response = await(connector.getSubscriptionStatus(sapNumber).value).value
            response.status shouldBe httpResponse.status
            response.body   shouldBe httpResponse.body
          }
        }
      }

      "return an error when the future fails" in {
        wireMockServer.stop()
        when(GET, expectedSubscriptionStatusUrl, headers = expectedHeaders.toMap)
        await(connector.getSubscriptionStatus(sapNumber).value).isLeft shouldBe true
        wireMockServer.start()
      }
    }
  }
}
