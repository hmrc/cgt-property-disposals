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

package uk.gov.hmrc.cgtpropertydisposals.service.onboarding

import java.time.LocalDateTime

import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.{JsNumber, JsValue, Json, Writes}
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.SubscriptionConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Country, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesSubscriptionUpdateRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.SubscriptionResponseEvent
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails, SubscriptionUpdateResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber}
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.email.EmailService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockSubscriptionConnector = mock[SubscriptionConnector]

  val mockEmailService = mock[EmailService]

  val mockAuditService: AuditService = mock[AuditService]

  val service = new SubscriptionServiceImpl(
    mockAuditService,
    mockSubscriptionConnector,
    mockEmailService,
    MockMetrics.metrics
  )

  def mockAuditSubscriptionResponse(
    httpStatus: Int,
    responseBody: Option[JsValue],
    desSubscriptionRequest: DesSubscriptionRequest
  ) =
    (mockAuditService
      .sendEvent(_: String, _: SubscriptionResponseEvent, _: String)(
        _: HeaderCarrier,
        _: Writes[SubscriptionResponseEvent],
        _: Request[_]
      ))
      .expects(
        "subscriptionResponse",
        SubscriptionResponseEvent(
          httpStatus,
          responseBody.getOrElse(Json.parse("""{ "body" : "could not parse body as JSON: " }""")),
          desSubscriptionRequest
        ),
        "subscription-response",
        *,
        *,
        *
      )
      .returning(())

  def mockSubscribe(expectedSubscriptionDetails: DesSubscriptionRequest)(response: Either[Error, HttpResponse]) =
    (mockSubscriptionConnector
      .subscribe(_: DesSubscriptionRequest)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscription(cgtReference: CgtReference)(response: Either[Error, HttpResponse]) =
    (mockSubscriptionConnector
      .getSubscription(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT(Future.successful(response)))

  def mockUpdateSubscriptionDetails(subscribedDetails: DesSubscriptionUpdateRequest, cgtReference: CgtReference)(
    response: Either[Error, HttpResponse]
  ) =
    (mockSubscriptionConnector
      .updateSubscription(_: DesSubscriptionUpdateRequest, _: CgtReference)(_: HeaderCarrier))
      .expects(subscribedDetails, cgtReference, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscriptionStatus(sapNumber: SapNumber)(response: Either[Error, HttpResponse]) =
    (mockSubscriptionConnector
      .getSubscriptionStatus(_: SapNumber)(_: HeaderCarrier))
      .expects(sapNumber, *)
      .returning(EitherT(Future.successful(response)))

  def mockSendConfirmationEmail(cgtReference: CgtReference, email: Email, contactName: ContactName)(
    response: Either[Error, Unit]
  ) =
    (mockEmailService
      .sendSubscriptionConfirmationEmail(_: CgtReference, _: Email, _: ContactName)(_: HeaderCarrier, _: Request[_]))
      .expects(cgtReference, email, contactName, *, *)
      .returning(EitherT(Future.successful(response)))

  private val emptyJsonBody = "{}"
  private val noJsonInBody  = ""

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
        Postcode("DH14EJ")
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

      val expectedUpdateRequest = DesSubscriptionUpdateRequest(expectedRequest)

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockUpdateSubscriptionDetails(expectedUpdateRequest, cgtReference)(Right(HttpResponse(500, emptyJsonBody)))
          await(service.updateSubscription(updatedDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockUpdateSubscriptionDetails(expectedUpdateRequest, cgtReference)(Right(HttpResponse(200, emptyJsonBody)))
          await(service.updateSubscription(updatedDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockUpdateSubscriptionDetails(expectedUpdateRequest, cgtReference)(
            Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]]))
          )
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
          val jsonBody       =
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

          mockUpdateSubscriptionDetails(expectedUpdateRequest, cgtReference)(
            Right(HttpResponse(200, Json.parse(jsonBody), Map.empty[String, Seq[String]]))
          )
          await(service.updateSubscription(updatedDetails).value) shouldBe Right(updateResponse)
        }
    }

    "handling requests to get subscription display details " must {
      implicit val hc: HeaderCarrier = HeaderCarrier()
      val cgtReference               = CgtReference("XFCGT123456789")

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockGetSubscription(cgtReference)(Right(HttpResponse(500, emptyJsonBody)))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockGetSubscription(cgtReference)(Right(HttpResponse(200, emptyJsonBody)))
          await(service.getSubscription(cgtReference).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockGetSubscription(cgtReference)(Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]])))
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

          mockGetSubscription(cgtReference)(
            Right(HttpResponse(200, Json.parse(jsonBody), Map.empty[String, Seq[String]]))
          )
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

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]])))
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

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]])))
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

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]])))
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

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]])))
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

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]])))
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

          mockGetSubscription(cgtReference)(Right(HttpResponse(200, jsonBody, Map.empty[String, Seq[String]])))
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

          val subscriptionDisplayResponse = SubscribedDetails(
            Left(TrustName("ABC Trust")),
            Email("stephen@abc.co.uk"),
            NonUkAddress("101 Kiwi Street", None, None, Some("Christchurch"), None, Country("NZ")),
            ContactName("Stephen Wood"),
            cgtReference,
            Some(TelephoneNumber("(+013)32752856")),
            true
          )
          mockGetSubscription(cgtReference)(
            Right(HttpResponse(200, Json.parse(jsonBody), Map.empty[String, Seq[String]]))
          )
          await(service.getSubscription(cgtReference).value) shouldBe Right(Some(subscriptionDisplayResponse))
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

          val subscriptionDisplayResponse = SubscribedDetails(
            Left(TrustName("ABC Trust")),
            Email("stephen@abc.co.uk"),
            NonUkAddress(
              "101 Kiwi Street",
              None,
              None,
              Some("Christchurch"),
              Some(Postcode("C11")),
              Country("NZ")
            ),
            ContactName("Stephen Wood"),
            cgtReference,
            Some(TelephoneNumber("(+013)32752856")),
            true
          )
          mockGetSubscription(cgtReference)(
            Right(HttpResponse(200, Json.parse(jsonBody), Map.empty[String, Seq[String]]))
          )
          await(service.getSubscription(cgtReference).value) shouldBe Right(Some(subscriptionDisplayResponse))
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

          val subscriptionDisplayResponse = SubscribedDetails(
            Right(IndividualName("Luke", "Bishop")),
            Email("stephen@abc.co.uk"),
            UkAddress("100 Sutton Street", Some("Wokingham"), Some("Surrey"), Some("London"), Postcode("DH14EJ")),
            ContactName("Stephen Wood"),
            cgtReference,
            Some(TelephoneNumber("(+013)32752856")),
            true
          )
          mockGetSubscription(cgtReference)(
            Right(HttpResponse(200, Json.parse(jsonBody), Map.empty[String, Seq[String]]))
          )
          await(service.getSubscription(cgtReference).value) shouldBe Right(Some(subscriptionDisplayResponse))
        }

      "return the subscription display response the country code does not have a country name" in {
        val jsonBody =
          s"""
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
            |            "countryCode": "ZZ"
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

        val subscriptionDisplayResponse = SubscribedDetails(
          Right(IndividualName("Luke", "Bishop")),
          Email("stephen@abc.co.uk"),
          NonUkAddress(
            "100 Sutton Street",
            Some("Wokingham"),
            Some("Surrey"),
            Some("London"),
            Some(Postcode("DH14EJ")),
            Country("ZZ")
          ),
          ContactName("Stephen Wood"),
          cgtReference,
          Some(TelephoneNumber("(+013)32752856")),
          true
        )
        mockGetSubscription(cgtReference)(
          Right(HttpResponse(200, Json.parse(jsonBody), Map.empty[String, Seq[String]]))
        )
        await(service.getSubscription(cgtReference).value) shouldBe Right(Some(subscriptionDisplayResponse))
      }

      "return None when no subscription details exist for the cgt reference given" in {
        List(
          Json.parse("""{ "code": "NOT_FOUND", "reason" : "Data not found for the provided Registration Number." }"""),
          Json.parse(
            """{ "failures": [ { "code": "NOT_FOUND", "reason" : "Data not found for the provided Registration Number." } ] }"""
          )
        ).foreach { desResponseBody =>
          mockGetSubscription(cgtReference)(Right(HttpResponse(404, desResponseBody, Map.empty[String, Seq[String]])))
          await(service.getSubscription(cgtReference).value) shouldBe Right(None)
        }

      }

    }

    "handling requests to subscribe" must {

      implicit val hc: HeaderCarrier   = HeaderCarrier()
      implicit val request: Request[_] = FakeRequest()

      val subscriptionDetails = sample[SubscriptionDetails]
      val subscriptionRequest = DesSubscriptionRequest(subscriptionDetails)

      "return an error" when {
        "the http call comes back with a status other than 200" in {
          mockSubscribe(subscriptionRequest)(Right(HttpResponse(500, noJsonInBody)))
          mockAuditSubscriptionResponse(500, None, subscriptionRequest)
          await(service.subscribe(subscriptionDetails).value).isLeft shouldBe true
        }

        "there is no JSON in the body of the http response" in {
          mockSubscribe(subscriptionRequest)(Right(HttpResponse(200, noJsonInBody)))
          mockAuditSubscriptionResponse(200, None, subscriptionRequest)
          await(service.subscribe(subscriptionDetails).value).isLeft shouldBe true
        }

        "the JSON body of the response cannot be parsed" in {
          mockSubscribe(subscriptionRequest)(Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]])))
          mockAuditSubscriptionResponse(200, Some(JsNumber(1)), subscriptionRequest)
          await(service.subscribe(subscriptionDetails).value).isLeft shouldBe true
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
            mockSubscribe(subscriptionRequest)(
              Right(HttpResponse(200, subscriptionResponseJsonBody, Map.empty[String, Seq[String]]))
            )
            mockAuditSubscriptionResponse(200, Some(subscriptionResponseJsonBody), subscriptionRequest)
            mockSendConfirmationEmail(cgtReference, subscriptionDetails.emailAddress, subscriptionDetails.contactName)(
              Right(HttpResponse(ACCEPTED, emptyJsonBody))
            )
          }

          await(service.subscribe(subscriptionDetails).value) shouldBe Right(
            SubscriptionSuccessful(cgtReference.value)
          )
        }

        "the call to send an email fails" in {
          inSequence {
            mockSubscribe(subscriptionRequest)(
              Right(HttpResponse(200, subscriptionResponseJsonBody, Map.empty[String, Seq[String]]))
            )
            mockAuditSubscriptionResponse(200, Some(subscriptionResponseJsonBody), subscriptionRequest)
            mockSendConfirmationEmail(cgtReference, subscriptionDetails.emailAddress, subscriptionDetails.contactName)(
              Left(Error(""))
            )
          }

          await(service.subscribe(subscriptionDetails).value) shouldBe Right(
            SubscriptionSuccessful(cgtReference.value)
          )
        }

        "the call to send an email comes back with a status other than 202" in {
          List(OK, BAD_REQUEST, FORBIDDEN, INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach { status =>
            inSequence {
              mockSubscribe(subscriptionRequest)(
                Right(HttpResponse(200, subscriptionResponseJsonBody, Map.empty[String, Seq[String]]))
              )
              mockAuditSubscriptionResponse(200, Some(subscriptionResponseJsonBody), subscriptionRequest)
              mockSendConfirmationEmail(
                cgtReference,
                subscriptionDetails.emailAddress,
                subscriptionDetails.contactName
              )(Right(HttpResponse(status, emptyJsonBody)))
            }
            await(service.subscribe(subscriptionDetails).value) shouldBe Right(
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

        inSequence {
          mockSubscribe(subscriptionRequest)(
            Right(HttpResponse(403, subscriptionResponseJsonBody, Map.empty[String, Seq[String]]))
          )
          mockAuditSubscriptionResponse(403, Some(subscriptionResponseJsonBody), subscriptionRequest)
        }

        await(service.subscribe(subscriptionDetails).value) shouldBe Right(AlreadySubscribed)
      }

    }

    "handling requests to get subscription status" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      "return an error" when {

        def testGetSubscriptionStatusError(
          sapNumber: SapNumber,
          response: => Either[Error, HttpResponse]
        ) = {
          mockGetSubscriptionStatus(sapNumber)(response)

          await(service.getSubscriptionStatus(sapNumber).value).isLeft shouldBe true
        }

        "the call to get subscription status fails" in {
          testGetSubscriptionStatusError(sample[SapNumber], Left(Error("")))
        }

        "the call to get subscription status returns with an error status code" in {
          testGetSubscriptionStatusError(sample[SapNumber], Right(HttpResponse(500, emptyJsonBody)))
        }

        "the response body to get subscription status contains no JSON" in {
          testGetSubscriptionStatusError(sample[SapNumber], Right(HttpResponse(200, emptyJsonBody)))
        }

        "the response body to get subscription status contains JSON which cannot be parsed" in {
          testGetSubscriptionStatusError(
            sample[SapNumber],
            Right(HttpResponse(200, JsNumber(1), Map.empty[String, Seq[String]]))
          )

        }

        "the response body to get subscription status does not contain a CGT reference id when " +
          "the status is subscribed" in {
            List(
              Json.parse("""{ "subscriptionStatus": "SUCCESSFUL" }"""),
              Json.parse("""{ "subscriptionStatus": "SUCCESSFUL", "idType": "TYPE", "idValue": "value"}"""),
              Json.parse("""{ "subscriptionStatus": "SUCCESSFUL", "idType": "ZCGT"}""")
            ).foreach { json =>
              withClue(s"For JSON $json ") {
                testGetSubscriptionStatusError(
                  sample[SapNumber],
                  Right(HttpResponse(200, json, Map.empty[String, Seq[String]]))
                )
              }
            }
          }

        "the response body to get subscription status contains an unknown status" in {
          testGetSubscriptionStatusError(
            sample[SapNumber],
            Right(
              HttpResponse(
                200,
                Json.parse("""{ "subscriptionStatus" : "HELLO" }"""),
                Map.empty[String, Seq[String]]
              )
            )
          )

        }

      }

      "return a cgt reference number" when {

        "the response indicates that a subscription already exists" in {
          val sapNumber    = sample[SapNumber]
          val responseBody = Json.parse(s"""
                                         |{
                                         |  "subscriptionStatus" : "SUCCESSFUL",
                                         |  "idType" : "ZCGT",
                                         |  "idValue" : "${cgtReference.value}"
                                         |}""".stripMargin)

          mockGetSubscriptionStatus(sapNumber)(Right(HttpResponse(200, responseBody, Map.empty[String, Seq[String]])))

          await(service.getSubscriptionStatus(sapNumber).value) shouldBe Right(Some(cgtReference))
        }

      }

      "return no cgt reference number" when {

        "the response hasa subscription status which is not 'SUCCESSFUL'" in {
          List(
            "NO_FORM_BUNDLE_FOUND",
            "REG_FORM_RECEIVED",
            "SENT_TO_DS",
            "DS_OUTCOME_IN_PROGRESS",
            "REJECTED",
            "IN_PROCESSING",
            "CREATE_FAILED",
            "WITHDRAWAL",
            "SENT_TO_RCM",
            "APPROVED_WITH_CONDITIONS",
            "REVOKED",
            "DE-REGISTERED",
            "CONTRACT_OBJECT_INACTIVE"
          ).foreach { status =>
            withClue(s"For status $status: ") {
              val notSubscribedJsonBody = Json.parse(s"""{ "subscriptionStatus" : "$status" }""")
              val sapNumber             = sample[SapNumber]

              mockGetSubscriptionStatus(sapNumber)(
                Right(HttpResponse(200, notSubscribedJsonBody, Map.empty[String, Seq[String]]))
              )

              await(service.getSubscriptionStatus(sapNumber).value) shouldBe Right(None)
            }

          }

        }

      }

    }

  }
}
