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
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
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

    implicit val hc: HeaderCarrier = HeaderCarrier()
    val expectedHeaders            = Map("Authorization" -> s"Bearer $desBearerToken", "Environment" -> desEnvironment)
    val expectedSubscriptionUrl    = "http://host:123/subscriptions/create/CGT"
    def expectedSubscriptionDisplayUrl(cgtReference: CgtReference) =
      s"http://host:123/subscriptions/CGT/ZCGT/${cgtReference.value}"

    "handling request to update subscription details" must {
      val cgtReference = CgtReference("XFCGT123456789")

      val expectedRequest = SubscribedDetails(
        Right(IndividualName("Stephen", "Wood")),
        Email("stephen@abc.co.uk"),
        UkAddress(
          "100 Sutton Street",
          Some("Wokingham"),
          Some("Surrey"),
          Some("London"),
          "DH14EJ"
        ),
        ContactName("Stephen Wood"),
        cgtReference,
        Some(TelephoneNumber("(+013)32752856")),
        true
      )

      val expectedDesSubUpdateRequest = DesSubscriptionUpdateRequest(expectedRequest)

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
          HttpResponse(200, Some(Json.parse(expectedResponse))),
          HttpResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPut(
              expectedSubscriptionDisplayUrl(cgtReference),
              expectedDesSubUpdateRequest
            )(
              Some(httpResponse)
            )

            await(connector.updateSubscription(expectedRequest).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {

        mockPut(expectedSubscriptionDisplayUrl(cgtReference), expectedDesSubUpdateRequest)(
          None
        )
        await(connector.updateSubscription(expectedRequest).value).isLeft shouldBe true
      }

      "be able to handle non uk addresses" in {

        val expectedRequest = SubscribedDetails(
          Left(TrustName("Trust")),
          Email("stefano@abc.co.uk"),
          NonUkAddress(
            "100 Via Suttono",
            Some("Wokingama"),
            Some("Surre"),
            Some("Londono"),
            Some("DH14EJ"),
            Country("IT", Some("Italy"))
          ),
          ContactName("Stefano Bosco"),
          cgtReference,
          Some(TelephoneNumber("(+013)32752856")),
          true
        )

        val expectedDesSubUpdateRequest = DesSubscriptionUpdateRequest(expectedRequest)
        val httpResponse                = HttpResponse(200)

        mockPut(expectedSubscriptionDisplayUrl(cgtReference), expectedDesSubUpdateRequest)(Some(httpResponse))

        await(connector.updateSubscription(expectedRequest).value) shouldBe Right(httpResponse)
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
          HttpResponse(200, Some(Json.parse(expectedResponse))),
          HttpResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockGet(
              expectedSubscriptionDisplayUrl(cgtReference),
              Map.empty,
              expectedHeaders
            )(
              Some(httpResponse)
            )

            await(connector.getSubscription(cgtReference).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockGet(expectedSubscriptionDisplayUrl(cgtReference), Map.empty, expectedHeaders)(
          None
        )

        await(connector.getSubscription(cgtReference).value).isLeft shouldBe true
      }

    }

    "handling request to subscribe for individuals" must {

      val subscriptionDetails = SubscriptionDetails(
        Right(IndividualName("name", "surname")),
        ContactName("contact"),
        Email("email"),
        UkAddress("line1", Some("line2"), Some("town"), Some("county"), "postcode"),
        SapNumber("sap")
      )

      val expectedRequest = Json.parse(
        """
          |{
          |  "regime": "CGT",
          |  "identity": {
          |    "idType": "sapNumber",
          |    "idValue": "sap"
          |  },
          |  "subscriptionDetails": {
          |    "typeOfPersonDetails": {
          |      "typeOfPerson": "Individual",
          |      "firstName": "name",
          |      "lastName": "surname"
          |    },
          |    "addressDetails": {
          |      "addressLine1": "line1",
          |      "addressLine2": "line2",
          |      "addressLine3": "town",
          |      "addressLine4": "county",
          |      "postalCode": "postcode",
          |      "countryCode": "GB"
          |    },
          |    "contactDetails": {
          |      "contactName": "contact",
          |      "emailAddress": "email"
          |    }
          |  }
          |}
          |""".stripMargin
      )

      "do a post http call and return the result" in {
        List(
          HttpResponse(200),
          HttpResponse(200, Some(JsString("hi"))),
          HttpResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPost(expectedSubscriptionUrl, expectedHeaders, expectedRequest)(
              Some(httpResponse)
            )

            await(connector.subscribe(subscriptionDetails).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockPost(expectedSubscriptionUrl, expectedHeaders, expectedRequest)(None)

        await(connector.subscribe(subscriptionDetails).value).isLeft shouldBe true
      }

    }

    "handling request to subscribe for trusts" must {

      val subscriptionDetails = SubscriptionDetails(
        Left(TrustName("name")),
        ContactName("contact"),
        Email("email"),
        NonUkAddress(
          "line1",
          Some("line2"),
          Some("line3"),
          Some("line4"),
          Some("postcode"),
          Country("HK", Some("Hong Kong"))
        ),
        SapNumber("sap")
      )

      val expectedRequest = Json.parse(
        """
          |{
          |  "regime": "CGT",
          |  "identity": {
          |    "idType": "sapNumber",
          |    "idValue": "sap"
          |  },
          |  "subscriptionDetails": {
          |    "typeOfPersonDetails": {
          |      "typeOfPerson": "Trustee",
          |      "organisationName": "name"
          |    },
          |    "addressDetails": {
          |      "addressLine1": "line1",
          |      "addressLine2": "line2",
          |      "addressLine3": "line3",
          |      "addressLine4": "line4",
          |      "postalCode": "postcode",
          |      "countryCode": "HK"
          |    },
          |    "contactDetails": {
          |      "contactName": "contact",
          |      "emailAddress": "email"
          |    }
          |  }
          |}
          |""".stripMargin
      )

      "do a post http call and return the result" in {
        List(
          HttpResponse(200),
          HttpResponse(200, Some(JsString("hi"))),
          HttpResponse(500)
        ).foreach { httpResponse =>
          withClue(s"For http response [${httpResponse.toString}]") {
            mockPost(expectedSubscriptionUrl, expectedHeaders, expectedRequest)(
              Some(httpResponse)
            )

            await(connector.subscribe(subscriptionDetails).value) shouldBe Right(httpResponse)
          }
        }
      }

      "return an error when the future fails" in {
        mockPost(expectedSubscriptionUrl, expectedHeaders, expectedRequest)(None)

        await(connector.subscribe(subscriptionDetails).value).isLeft shouldBe true
      }

    }
  }

}
