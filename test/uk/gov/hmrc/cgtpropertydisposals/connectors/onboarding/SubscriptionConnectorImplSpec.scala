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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

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

  val connector = new SubscriptionConnectorImpl(mockHttp, new ServicesConfig(config))

  private val emptyJsonBody = "{}"

  "SubscriptionConnectorImpl" when {

    implicit val hc: HeaderCarrier                                 = HeaderCarrier()
    val expectedHeaders                                            = Seq("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)
    val expectedSubscriptionUrl                                    = "http://host:123/subscriptions/create/CGT"
    def expectedSubscriptionDisplayUrl(cgtReference: CgtReference) =
      s"http://host:123/subscriptions/CGT/ZCGT/${cgtReference.value}"

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
            mockPut(
              expectedSubscriptionDisplayUrl(cgtReference),
              Json.toJson(request)
            )(
              Some(httpResponse)
            )

            await(connector.updateSubscription(request, cgtReference).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {

        mockPut(expectedSubscriptionDisplayUrl(cgtReference), Json.toJson(request))(
          None
        )
        await(connector.updateSubscription(request, cgtReference).value).isLeft shouldBe true
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
            mockGetWithQueryWithHeaders(
              expectedSubscriptionDisplayUrl(cgtReference),
              Seq.empty,
              expectedHeaders
            )(
              Some(httpResponse)
            )

            await(connector.getSubscription(cgtReference).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockGetWithQueryWithHeaders(expectedSubscriptionDisplayUrl(cgtReference), Seq.empty, expectedHeaders)(
          None
        )

        await(connector.getSubscription(cgtReference).value).isLeft shouldBe true
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
            mockPost(expectedSubscriptionUrl, expectedHeaders, Json.toJson(request))(
              Some(httpResponse)
            )

            await(connector.subscribe(request).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockPost(expectedSubscriptionUrl, expectedHeaders, Json.toJson(request))(None)

        await(connector.subscribe(request).value).isLeft shouldBe true
      }

    }

    "handling requests to get subscription status" must {

      val sapNumber                     = SapNumber("sap")
      val expectedSubscriptionStatusUrl = s"http://host:123/cross-regime/subscription/CGT/${sapNumber.value}/status"

      "do a post http call and return the result" in {
        List(
          HttpResponse(200, "{}"),
          HttpResponse(200, JsString("hi"), Map.empty[String, Seq[String]]),
          HttpResponse(500, "{}")
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGetWithQueryWithHeaders(expectedSubscriptionStatusUrl, Seq.empty, expectedHeaders)(
              Some(httpResponse)
            )

            await(connector.getSubscriptionStatus(sapNumber).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockGetWithQueryWithHeaders(expectedSubscriptionStatusUrl, Seq.empty, expectedHeaders)(None)

        await(connector.getSubscriptionStatus(sapNumber).value).isLeft shouldBe true
      }

    }
  }

}
