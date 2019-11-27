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

import java.time.LocalDateTime

import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.libs.json.{JsNumber, Json}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.{EmailConnector, SubscriptionConnector}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.subscription._
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber, subscription}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class SubscriptionServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockSubscriptionConnector = mock[SubscriptionConnector]

  val mockEmailConnector = mock[EmailConnector]

  val mockAuditService: AuditService = mock[AuditService]

  val nonIsoCountryCode = "XZ"

  val path = "path"

  val config = Configuration(
    ConfigFactory.parseString(
      s"""
        |des.non-iso-country-codes = ["$nonIsoCountryCode"]
        |""".stripMargin
    )
  )

  val service = new SubscriptionServiceImpl(mockAuditService, mockSubscriptionConnector, mockEmailConnector, config)

  def mockSubscriptionResponse(httpStatus: Int, httpBody: String, path: String)(response: Unit) =
    (mockAuditService
      .sendSubscriptionResponse(_: Int, _: String, _: String)(_: HeaderCarrier, _: ExecutionContext))
      .expects(httpStatus, *, path, *, *)
      .returning(response)

  def mockSubscriptionEmailEvent(email: String, cgtReference: String, path: String)(response: Unit) =
    (mockAuditService
      .sendSubscriptionConfirmationEmailSentEvent(_: String, _: String, _: String)(
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(email, *, path, *, *)
      .returning(response)

  def mockSubscribe(expectedSubscriptionDetails: SubscriptionDetails)(response: Either[Error, HttpResponse]) =
    (mockSubscriptionConnector
      .subscribe(_: SubscriptionDetails)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscription(cgtReference: CgtReference)(response: Either[Error, HttpResponse]) =
    (mockSubscriptionConnector
      .getSubscription(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT(Future.successful(response)))

  def mockUpdateSubscriptionDetails(subscribedDetails: SubscribedDetails)(
    response: Either[Error, HttpResponse]
  ) =
    (mockSubscriptionConnector
      .updateSubscription(_: SubscribedDetails)(_: HeaderCarrier))
      .expects(subscribedDetails, *)
      .returning(EitherT(Future.successful(response)))

  def mockSendConfirmationEmail(subscriptionDetails: SubscriptionDetails, cgtReference: CgtReference)(
    response: Either[Error, HttpResponse]
  ) =
    (mockEmailConnector
      .sendSubscriptionConfirmationEmail(_: SubscriptionDetails, _: CgtReference)(_: HeaderCarrier))
      .expects(subscriptionDetails, cgtReference, *)
      .returning(EitherT(Future.successful(response)))

  "SubscriptionServiceImpl" when {
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

    val updatedDetails = SubscribedUpdateDetails(
      expectedRequest,
      expectedRequest
    )

    "handling requests to update subscription details " must {
      implicit val hc: HeaderCarrier = HeaderCarrier()

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockUpdateSubscriptionDetails(expectedRequest)(Right(HttpResponse(500)))
          await(service.updateSubscription(updatedDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockUpdateSubscriptionDetails(expectedRequest)(Right(HttpResponse(200)))
          await(service.updateSubscription(updatedDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockUpdateSubscriptionDetails(expectedRequest)(Right(HttpResponse(200, Some(JsNumber(1)))))
          await(service.updateSubscription(updatedDetails).value).isLeft shouldBe true
        }
      }

      "return the subscription update response if the call comes back with a " +
        "200 status and the JSON body can be parsed" in {
        val updateResponse =
          SubscriptionUpdateResponse(
            "CGT",
            LocalDateTime.now(),
            "form",
            "cgtRef",
            "GB",
            Some("postcode")
          )
        val jsonBody =
          s"""
            |{
            | "regime": "CGT",
            | "processingDate": "${updateResponse.processingDate.toString}",
            | "formBundleNumber": "${updateResponse.formBundleNumber}",
            | "cgtReferenceNumber": "${updateResponse.cgtReferenceNumber}",
            | "countryCode": "${updateResponse.countryCode}",
            | "postalCode": "postcode"
            |}
            |""".stripMargin

        mockUpdateSubscriptionDetails(expectedRequest)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
        await(service.updateSubscription(updatedDetails).value) shouldBe Right(updateResponse)
      }
    }

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

        "an email cannot be found in the response" in {
          val jsonBody =
            """
              |{
              |    "regime": "CGT",
              |    "subscriptionDetails": {
              |        "typeOfPersonDetails": {
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
              |            "faxNumber": "01332754256"
              |        },
              |        "isRegisteredWithId": true
              |    }
              |}
              |""".stripMargin

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "some invalid person type is in the response" in {
          val jsonBody =
            Json.parse("""
              |{
              |    "regime": "CGT",
              |    "subscriptionDetails": {
              |        "typeOfPersonDetails": {
              |            "typeOfPerson": "bad person type"
              |        },
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
              |        },
              |        "isRegisteredWithId": true
              |    }
              |}
              |""".stripMargin)

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(jsonBody))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "no organisation name or individual's name can be found in the response" in {
          val jsonBody =
            Json.parse("""
              |{
              |    "regime": "CGT",
              |    "subscriptionDetails": {
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
              |        },
              |        "isRegisteredWithId": true
              |    }
              |}
              |""".stripMargin)

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(jsonBody))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "type of person is individual but last name is missing" in {
          val jsonBody =
            Json.parse("""
                         |{
                         |    "regime": "CGT",
                         |    "subscriptionDetails": {
                         |        "typeOfPersonDetails": {
                         |            "typeOfPerson": "Individual",
                         |            "firstName" : "Joe"
                         |        },
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
                         |        },
                         |        "isRegisteredWithId": true
                         |    }
                         |}
                         |""".stripMargin)

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(jsonBody))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "type of person is individual and first and last name is missing" in {
          val jsonBody =
            Json.parse("""
                         |{
                         |    "regime": "CGT",
                         |    "subscriptionDetails": {
                         |        "typeOfPersonDetails": {
                         |            "typeOfPerson": "Individual"
                         |        },
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
                         |        },
                         |        "isRegisteredWithId": true
                         |    }
                         |}
                         |""".stripMargin)

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(jsonBody))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "type of person is individual but first name is missing" in {
          val jsonBody =
            Json.parse("""
                         |{
                         |    "regime": "CGT",
                         |    "subscriptionDetails": {
                         |        "typeOfPersonDetails": {
                         |            "typeOfPerson": "Individual",
                         |            "lastName" : "Smith"
                         |        },
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
                         |        },
                         |        "isRegisteredWithId": true
                         |    }
                         |}
                         |""".stripMargin)

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(jsonBody))))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "type of person is trustee but organisation name is missing" in {
          val jsonBody =
            Json.parse("""
                         |{
                         |    "regime": "CGT",
                         |    "subscriptionDetails": {
                         |        "typeOfPersonDetails": {
                         |            "typeOfPerson": "Trustee"
                         |        },
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
                         |        },
                         |        "isRegisteredWithId": true
                         |    }
                         |}
                         |""".stripMargin)

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, Some(jsonBody))))
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
            |        "typeOfPersonDetails": {
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

        val subscriptionDisplayResponse = subscription.SubscribedDetails(
          Left(TrustName("ABC Trust")),
          Email("stephen@abc.co.uk"),
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
            |        "typeOfPersonDetails": {
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

        val subscriptionDisplayResponse = subscription.SubscribedDetails(
          Left(TrustName("ABC Trust")),
          Email("stephen@abc.co.uk"),
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
            |        "typeOfPersonDetails": {
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

        val subscriptionDisplayResponse = subscription.SubscribedDetails(
          Right(IndividualName("Luke", "Bishop")),
          Email("stephen@abc.co.uk"),
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
          mockSubscriptionResponse(500, "", path)(())
          await(service.subscribe(subscriptionDetails, path).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockSubscribe(subscriptionDetails)(Right(HttpResponse(200)))
          mockSubscriptionResponse(200, "", path)(())
          await(service.subscribe(subscriptionDetails, path).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockSubscribe(subscriptionDetails)(Right(HttpResponse(200, Some(JsNumber(1)))))
          mockSubscriptionResponse(200, "", path)(())
          await(service.subscribe(subscriptionDetails, path).value).isLeft shouldBe true
        }
      }

      "return the subscription successful response" when {

        val subscriptionResponseJsonBody = Json.parse(
          s"""
             |{
             |  "cgtReferenceNumber" : "${cgtReference.value}"
             |}
             |""".stripMargin
        )

        "the subscription call comes back with a 200 status and the JSON body can be parsed" in {
          inSequence {
            mockSubscribe(subscriptionDetails)(Right(HttpResponse(200, Some(subscriptionResponseJsonBody))))
            mockSubscriptionResponse(200, subscriptionResponseJsonBody.toString(), path)(())
            mockSendConfirmationEmail(subscriptionDetails, cgtReference)(Right(HttpResponse(ACCEPTED)))
            mockSubscriptionEmailEvent(subscriptionDetails.emailAddress.value, cgtReference.value, path)(())
          }

          await(service.subscribe(subscriptionDetails, path).value) shouldBe Right(
            SubscriptionSuccessful(cgtReference.value))
        }

        "the call to send an email fails" in {
          inSequence {
            mockSubscribe(subscriptionDetails)(Right(HttpResponse(200, Some(subscriptionResponseJsonBody))))
            mockSubscriptionResponse(200, subscriptionResponseJsonBody.toString(), path)(())
            mockSendConfirmationEmail(subscriptionDetails, cgtReference)(Left(Error("")))
          }

          await(service.subscribe(subscriptionDetails, path).value) shouldBe Right(
            SubscriptionSuccessful(cgtReference.value))
        }

        "the call to send an email comes back with a status other than 202" in {
          List(OK, BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { status =>
            inSequence {
              mockSubscribe(subscriptionDetails)(Right(HttpResponse(200, Some(subscriptionResponseJsonBody))))
              mockSubscriptionResponse(200, subscriptionResponseJsonBody.toString(), path)(())
              mockSendConfirmationEmail(subscriptionDetails, cgtReference)(Right(HttpResponse(status)))
              mockSubscriptionEmailEvent(subscriptionDetails.emailAddress.value, cgtReference.value, path)(())
            }
            await(service.subscribe(subscriptionDetails, path).value) shouldBe Right(
              SubscriptionSuccessful(cgtReference.value)
            )
          }
        }
      }

      "return an already subscribed response if the response from DES indicates as such" in {
        val subscriptionResponseJsonBody = Json.parse(
          s"""
             |{
             |  "code" :   "ACTIVE_SUBSCRIPTION",
             |  "reason" : "The remote endpoint has responded that there is already an active subscription for the CGT regime."
             |}
             |""".stripMargin
        )

        mockSubscribe(subscriptionDetails)(Right(HttpResponse(403, Some(subscriptionResponseJsonBody))))

        mockSubscriptionResponse(403, "", path)(())
        await(service.subscribe(subscriptionDetails, path).value) shouldBe Right(AlreadySubscribed)

      }

    }
  }
}
