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

package uk.gov.hmrc.cgtpropertydisposals.service

import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.SubscriptionConnector
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.ContactName
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, SubscriptionDetails, SubscriptionDisplayResponse, SubscriptionResponse, TelephoneNumber, sample}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import org.scalacheck.ScalacheckShapeless._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector = mock[SubscriptionConnector]

  val nonIsoCountryCode = "XZ"

  val config = Configuration(
    ConfigFactory.parseString(
      s"""
        |des.non-iso-country-codes = ["$nonIsoCountryCode"]
        |""".stripMargin
    )
  )

  val service = new SubscriptionServiceImpl(mockConnector, config)

  def mockSubscribe(expectedSubscriptionDetails: SubscriptionDetails)(response: Either[Error, HttpResponse]) =
    (mockConnector
      .subscribe(_: SubscriptionDetails)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscription(cgtReference: CgtReference)(response: Either[Error, HttpResponse]) =
    (mockConnector
      .getSubscription(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT(Future.successful(response)))

  "SubscriptionServiceImpl" when {

    "handling requests to get subscription display details " must {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val cgtReference               = CgtReference("XFCGT123456789")

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockGetSubscription(cgtReference)(Right(HttpResponse(500)))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockGetSubscription(cgtReference)(Right(HttpResponse(200)))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(JsNumber(1)))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }
      }
      "return the subscription display response if the call comes back with a " +
        "200 status and the JSON body can be parsed and the address is a Non-UK address and no post code" in {
        val jsonBody =
          """
            |{
            |    "regime": "CGT",
            |    "subscriptionDetails": {
            |        "trustee": {
            |            "typeOfPerson": "Trustee",
            |            "organisationName": "ABC Trust"
            |        },
            |        "isRegisteredWithId": true,
            |        "addressDetails": {
            |            "addressLine1": "101 Kiwi Street",
            |            "addressLine4": "Christchurch",
            |            "countryCode": "NZ"
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

        val subscriptionDisplayResponse = SubscriptionDisplayResponse(
          Some(Email("stephen@abc.co.uk")),
          NonUkAddress("101 Kiwi Street", None, None, Some("Christchurch"), None, Country("NZ", Some("New Zealand"))),
          ContactName("Stephen Wood"),
          cgtReference,
          Some(TelephoneNumber("(+013)32752856")),
          true
        )
        mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
        await(service.getSubscription(cgtReference).value) shouldBe Right(subscriptionDisplayResponse)
      }

      "return the subscription display response if the call comes back with a " +
        "200 status and the JSON body can be parsed and the address is a Non-UK address with a post code" in {
        val jsonBody =
          """
            |{
            |    "regime": "CGT",
            |    "subscriptionDetails": {
            |        "trustee": {
            |            "typeOfPerson": "Trustee",
            |            "organisationName": "ABC Trust"
            |        },
            |        "isRegisteredWithId": true,
            |        "addressDetails": {
            |            "addressLine1": "101 Kiwi Street",
            |            "addressLine4": "Christchurch",
            |            "postalCode": "C11",
            |            "countryCode": "NZ"
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

        val subscriptionDisplayResponse = SubscriptionDisplayResponse(
          Some(Email("stephen@abc.co.uk")),
          NonUkAddress(
            "101 Kiwi Street",
            None,
            None,
            Some("Christchurch"),
            Some("C11"),
            Country("NZ", Some("New Zealand"))
          ),
          ContactName("Stephen Wood"),
          cgtReference,
          Some(TelephoneNumber("(+013)32752856")),
          true
        )
        mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
        await(service.getSubscription(cgtReference).value) shouldBe Right(subscriptionDisplayResponse)
      }

      "return the subscription display response if the call comes back with a " +
        "200 status and the JSON body can be parsed and the address is a UK address" in {
        val jsonBody =
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

        val subscriptionDisplayResponse = SubscriptionDisplayResponse(
          Some(Email("stephen@abc.co.uk")),
          UkAddress("100 Sutton Street", Some("Wokingham"), Some("Surrey"), Some("London"), "DH14EJ"),
          ContactName("Stephen Wood"),
          cgtReference,
          Some(TelephoneNumber("(+013)32752856")),
          true
        )
        mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
        await(service.getSubscription(cgtReference).value) shouldBe Right(subscriptionDisplayResponse)
      }
    }

    "handling requests to subscribe" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()
      val subscriptionDetails        = sample[SubscriptionDetails]

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockSubscribe(subscriptionDetails)(Right(HttpResponse(500)))
          await(service.subscribe(subscriptionDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockSubscribe(subscriptionDetails)(Right(HttpResponse(200)))
          await(service.subscribe(subscriptionDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockSubscribe(subscriptionDetails)(Right(HttpResponse(200, Some(JsNumber(1)))))
          await(service.subscribe(subscriptionDetails).value).isLeft shouldBe true
        }
      }
      "return the subscription response if the call comes back with a " +
        "200 status and the JSON body can be parsed" in {
        val cgtReferenceNumber = "number"
        val jsonBody = Json.parse(
          s"""
             |{
             |  "cgtReferenceNumber" : "$cgtReferenceNumber"
             |}
             |""".stripMargin
        )
        mockSubscribe(subscriptionDetails)(Right(HttpResponse(200, Some(jsonBody))))
        await(service.subscribe(subscriptionDetails).value) shouldBe Right(SubscriptionResponse(cgtReferenceNumber))
      }
    }
  }
}
