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

package uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding

import java.time.LocalDateTime

import cats.data.EitherT
import cats.instances.future._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.{AuthenticateActions, AuthenticatedRequest}
import uk.gov.hmrc.cgtpropertydisposals.controllers.{ControllerSpec, onboarding}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.{SubscribedDetails, SubscribedUpdateDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.{RegisteredWithoutId, RegistrationDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscriptionDetails, SubscriptionResponse, SubscriptionUpdateResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.{RegisterWithoutIdService, SubscriptionService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubscriptionControllerSpec extends ControllerSpec with ScalaCheckDrivenPropertyChecks {

  val mockSubscriptionService      = mock[SubscriptionService]
  val mockTaxEnrolmentService      = mock[TaxEnrolmentService]
  val mockRegisterWithoutIdService = mock[RegisterWithoutIdService]

  val headerCarrier = HeaderCarrier()

  val fixedTimestamp = LocalDateTime.of(2019, 9, 24, 15, 47, 20)

  override val overrideBindings: List[GuiceableModule] =
    List(
      bind[AuthenticateActions].toInstance(Fake.login(Fake.user, fixedTimestamp)),
      bind[SubscriptionService].toInstance(mockSubscriptionService),
      bind[TaxEnrolmentService].toInstance(mockTaxEnrolmentService),
      bind[RegisterWithoutIdService].toInstance(mockRegisterWithoutIdService)
    )

  lazy val controller = instanceOf[SubscriptionController]

  def mockSubscribe(expectedSubscriptionDetails: SubscriptionDetails, path: String)(
    response: Either[Error, SubscriptionResponse]
  ) =
    (mockSubscriptionService
      .subscribe(_: SubscriptionDetails, _: String)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, path, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscription(cgtReference: CgtReference)(response: Either[Error, SubscribedDetails]): Unit =
    (mockSubscriptionService
      .getSubscription(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT.fromEither(response))

  def mockUpdateSubscription(subscribedUpdateDetails: SubscribedUpdateDetails)(
    response: Either[Error, SubscriptionUpdateResponse]
  ): Unit =
    (mockSubscriptionService
      .updateSubscription(_: SubscribedUpdateDetails)(_: HeaderCarrier))
      .expects(subscribedUpdateDetails, *)
      .returning(EitherT.fromEither(response))

  def mockUpdateVerifiers(updateVerifierDetails: UpdateVerifiersRequest)(
    response: Either[Error, Unit]
  ): Unit =
    (mockTaxEnrolmentService
      .updateVerifiers(_: UpdateVerifiersRequest)(_: HeaderCarrier))
      .expects(updateVerifierDetails, *)
      .returning(EitherT.fromEither(response))

  def mockAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Unit]
  ) =
    (mockTaxEnrolmentService
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT(Future.successful(response)))

  def mockCheckCgtEnrolmentExists(ggCredId: String)(response: Either[Error, Option[TaxEnrolmentRequest]]) =
    (mockTaxEnrolmentService
      .hasCgtSubscription(_: String)(_: HeaderCarrier))
      .expects(ggCredId, *)
      .returning(EitherT(Future.successful(response)))

  def mockRegisterWithoutId(registrationDetails: RegistrationDetails)(response: Either[Error, SapNumber]): Unit =
    (mockRegisterWithoutIdService
      .registerWithoutId(_: RegistrationDetails)(_: HeaderCarrier))
      .expects(registrationDetails, *)
      .returning(EitherT.fromEither(response))

  val (nonUkCountry, nonUkCountryCode) = Country("HK", Some("Hong Kong")) -> "HK"

  val taxEnrolmentRequestWithNonUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    "XACGTP123456789",
    Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), nonUkCountry)
  )

  "The SubscriptionController" when {

    "handling request to update subscription details" must {

      "return a bad request response if the request body is corrupt" in {

        val corruptRequestBody =
          """
            |{
            |   "bad-field":"bad-value"
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest().withJsonBody(Json.parse(corruptRequestBody))
          )

        val result = controller.updateSubscription()(request)
        status(result) shouldBe BAD_REQUEST

      }

      "return a 500 internal error response if there is a backend error" in {

        val subscribedDetails =
          """
            |{
            |    "newDetails": {
            |        "name": {
            |            "l": {
            |                "value": "ABC Trust"
            |            }
            |        },
            |        "emailAddress": "stefano@abc.co.uk",
            |        "address": {
            |            "NonUkAddress": {
            |                "line1": "101 Kiwi Street",
            |                "country": {
            |                    "code": "NZ",
            |                    "name": "New Zealand"
            |                }
            |            }
            |        },
            |        "contactName": "Stefano Bosco",
            |        "cgtReference": {
            |            "value": "XFCGT123456789"
            |        },
            |        "registeredWithId": true
            |    },
            |    "previousDetails": {
            |        "name": {
            |            "l": {
            |                "value": "ABC Trust"
            |            }
            |        },
            |        "emailAddress": "stefano@abc.co.uk",
            |        "address": {
            |            "NonUkAddress": {
            |                "line1": "101 Kiwi Street",
            |                "country": {
            |                    "code": "NZ",
            |                    "name": "New Zealand"
            |                }
            |            }
            |        },
            |        "contactName": "Stefano Bosco",
            |        "cgtReference": {
            |            "value": "XFCGT123456789"
            |        },
            |        "registeredWithId": true
            |    }
            |}
            |""".stripMargin

        val cgtReference = CgtReference("XFCGT123456789")

        val subscribedUpdateDetails = SubscribedUpdateDetails(
          SubscribedDetails(
            Left(TrustName("ABC Trust")),
            Email("stefano@abc.co.uk"),
            NonUkAddress(
              "101 Kiwi Street",
              None,
              None,
              None,
              None,
              Country("NZ", Some("New Zealand"))
            ),
            ContactName("Stefano Bosco"),
            cgtReference,
            None,
            true
          ),
          SubscribedDetails(
            Left(TrustName("ABC Trust")),
            Email("stefano@abc.co.uk"),
            NonUkAddress(
              "101 Kiwi Street",
              None,
              None,
              None,
              None,
              Country("NZ", Some("New Zealand"))
            ),
            ContactName("Stefano Bosco"),
            cgtReference,
            None,
            true
          )
        )

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest().withJsonBody(Json.parse(subscribedDetails))
          )

        mockUpdateSubscription(subscribedUpdateDetails)(Left(Error("Des error")))

        val result = controller.updateSubscription()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return a 200 OK response if the subscription is updated" in {

        val subscriptionUpdateDetailsRequest =
          """
            |{
            |    "newDetails": {
            |        "name": {
            |            "l": {
            |                "value": "ABC Trust"
            |            }
            |        },
            |        "emailAddress": "stefano@abc.co.uk",
            |        "address": {
            |            "NonUkAddress": {
            |                "line1": "121 Kiwi Street",
            |                "country": {
            |                    "code": "NZ",
            |                    "name": "New Zealand"
            |                }
            |            }
            |        },
            |        "contactName": "Stefano Bosco",
            |        "cgtReference": {
            |            "value": "XFCGT123456789"
            |        },
            |        "registeredWithId": true
            |    },
            |    "previousDetails": {
            |        "name": {
            |            "l": {
            |                "value": "ABC Trust"
            |            }
            |        },
            |        "emailAddress": "stefano@abc.co.uk",
            |        "address": {
            |            "NonUkAddress": {
            |                "line1": "101 Kiwi Street",
            |                "country": {
            |                    "code": "NZ",
            |                    "name": "New Zealand"
            |                }
            |            }
            |        },
            |        "contactName": "Stefano Bosco",
            |        "cgtReference": {
            |            "value": "XFCGT123456789"
            |        },
            |        "registeredWithId": true
            |    }
            |}
            |""".stripMargin

        val subscribedUpdateDetails = SubscribedUpdateDetails(
          SubscribedDetails(
            Left(TrustName("ABC Trust")),
            Email("stefano@abc.co.uk"),
            NonUkAddress(
              "121 Kiwi Street",
              None,
              None,
              None,
              None,
              Country("NZ", Some("New Zealand"))
            ),
            ContactName("Stefano Bosco"),
            CgtReference("XFCGT123456789"),
            None,
            true
          ),
          SubscribedDetails(
            Left(TrustName("ABC Trust")),
            Email("stefano@abc.co.uk"),
            NonUkAddress(
              "101 Kiwi Street",
              None,
              None,
              None,
              None,
              Country("NZ", Some("New Zealand"))
            ),
            ContactName("Stefano Bosco"),
            CgtReference("XFCGT123456789"),
            None,
            true
          )
        )

        val updateVerifiersRequest = UpdateVerifiersRequest(
          "ggCredId",
          subscribedUpdateDetails
        )

        val expectedSubscriptionUpdateResponse =
          """
            |{
            |    "regime": "CGT",
            |    "processingDate" : "2015-12-17T09:30:47",
            |    "formBundleNumber": "012345678901",
            |    "cgtReferenceNumber": "XXCGTP123456789",
            |    "countryCode": "GB",
            |    "postalCode" : "TF34NT"
            |}
            |""".stripMargin

        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest().withJsonBody(Json.parse(subscriptionUpdateDetailsRequest))
          )

        inSequence {
          mockUpdateSubscription(subscribedUpdateDetails)(
            Right(
              SubscriptionUpdateResponse(
                "CGT",
                LocalDateTime.of(2015, 12, 17, 9, 30, 47),
                "012345678901",
                "XXCGTP123456789",
                "GB",
                Some("TF34NT")
              )
            )
          )
          mockUpdateVerifiers(updateVerifiersRequest)(Right(()))
        }
        val result = controller.updateSubscription()(request)
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.parse(expectedSubscriptionUpdateResponse)
      }
    }

    "handling request to get subscription details" must {

      "return a 200 OK response if the a subscription exists" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest()
          )

        mockGetSubscription(CgtReference("XDCGT123456789"))(
          Right(
            SubscribedDetails(
              Right(IndividualName("Stephen", "Wood")),
              Email("stephen@abc.co.uk"),
              UkAddress("100 Sutton Street", Some("Wokingham"), Some("Surrey"), Some("London"), "DH14EJ"),
              ContactName("Stephen Wood"),
              CgtReference("XDCGT123456789"),
              Some(TelephoneNumber("+44191919191919")),
              true
            )
          )
        )

        val expectedResponse =
          """
            |{
            |    "name" : {"r":{"firstName":"Stephen","lastName":"Wood"}},
            |    "emailAddress": "stephen@abc.co.uk",
            |    "address": {
            |        "UkAddress": {
            |            "postcode": "DH14EJ",
            |            "line1": "100 Sutton Street",
            |            "line2": "Wokingham",
            |            "town": "Surrey",
            |            "county": "London",
            |            "postcode": "DH14EJ"
            |        }
            |    },
            |    "contactName": "Stephen Wood",
            |    "cgtReference": {
            |        "value": "XDCGT123456789"
            |    },
            |    "telephoneNumber": "+44191919191919",
            |    "registeredWithId": true
            |}
            |""".stripMargin

        val result = controller.getSubscription(CgtReference("XDCGT123456789"))(request)
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.parse(expectedResponse)
      }

      "return an internal server error" when {
        "there is a problem while trying to get the subscription display details" in {
          val request =
            new AuthenticatedRequest(
              Fake.user,
              LocalDateTime.now(),
              headerCarrier,
              FakeRequest()
            )

          mockGetSubscription(CgtReference("XDCGT123456789"))(Left(Error("Could not get subscription details")))

          val result = controller.getSubscription(CgtReference("XDCGT123456789"))(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }

    "handling requests to check if a user has subscribed already" must {

      "return a 200 OK response if the user has subscribed" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest().withJsonBody(JsString("hi"))
          )
        mockCheckCgtEnrolmentExists(Fake.user.ggCredId)(
          Right(Some(TaxEnrolmentRequest("ggCredId", "cgt-reference", UkAddress("line1", None, None, None, ""))))
        )
        val result = controller.checkSubscriptionStatus()(request)
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.obj("value" -> JsString("cgt-reference"))
      }

      "return a No Content response if the user has no enrolment request" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest().withJsonBody(JsString("hi"))
          )
        mockCheckCgtEnrolmentExists(Fake.user.ggCredId)(Right(None))
        val result = controller.checkSubscriptionStatus()(request)
        status(result) shouldBe NO_CONTENT
      }

      "return a internal server error response if a back end error occurred" in {
        val request =
          new AuthenticatedRequest(
            Fake.user,
            LocalDateTime.now(),
            headerCarrier,
            FakeRequest().withJsonBody(JsString("hi"))
          )
        mockCheckCgtEnrolmentExists(Fake.user.ggCredId)(Left(Error("Some back end error")))
        val result = controller.checkSubscriptionStatus()(request)
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

    }

    "handling requests to subscribe" must {

      def performAction(requestBody: Option[JsValue]): Future[Result] =
        controller.subscribe()(
          requestBody.fold[FakeRequest[AnyContent]](FakeRequest())(json => FakeRequest().withJsonBody(json))
        )

      val subscriptionDetails            = sample[SubscriptionDetails]
      val subscriptionDetailsJson        = Json.toJson(subscriptionDetails)
      val subscriptionSuccessfulResponse = sample[SubscriptionSuccessful]
      val taxEnrolmentRequest = TaxEnrolmentRequest(
        Fake.user.ggCredId,
        subscriptionSuccessfulResponse.cgtReferenceNumber,
        subscriptionDetails.address,
        fixedTimestamp
      )

      "return a bad request" when {

        "there is no JSON body in the request" in {
          val result = performAction(None)
          status(result) shouldBe BAD_REQUEST
        }

        "the JSON body cannot be parsed in the request" in {
          val result = performAction(Some(JsString("Hi")))
          status(result) shouldBe BAD_REQUEST
        }

      }

      "return an internal server error" when {

        "there is a problem while trying to subscribe" in {
          mockSubscribe(subscriptionDetails, onboarding.routes.SubscriptionController.subscribe().url)(
            Left(Error("oh no!"))
          )

          val result = performAction(Some(subscriptionDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

        "subscription is not successful if the tax enrolment service returns an error" in {
          inSequence {
            mockSubscribe(subscriptionDetails, onboarding.routes.SubscriptionController.subscribe().url)(
              Right(subscriptionSuccessfulResponse)
            )
            mockAllocateEnrolment(taxEnrolmentRequest)(
              Left(Error("Failed to allocate tax enrolment"))
            )
          }

          val result = performAction(Some(subscriptionDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a conflict" when {

        "the user has already subscribed to cgt" in {
          mockSubscribe(subscriptionDetails, onboarding.routes.SubscriptionController.subscribe().url)(
            Right(AlreadySubscribed)
          )

          val result = performAction(Some(subscriptionDetailsJson))
          status(result) shouldBe CONFLICT
        }

      }

      "return the subscription response" when {

        "subscription is successful" in {
          inSequence {
            mockSubscribe(subscriptionDetails, onboarding.routes.SubscriptionController.subscribe().url)(
              Right(subscriptionSuccessfulResponse)
            )
            mockAllocateEnrolment(taxEnrolmentRequest)(Right(()))
          }

          val result = performAction(Some(subscriptionDetailsJson))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(subscriptionSuccessfulResponse)
        }
      }
    }

    "handling requests to register without id" must {

      def performAction(requestBody: Option[JsValue]): Future[Result] =
        controller.registerWithoutId()(
          requestBody.fold[FakeRequest[AnyContent]](FakeRequest())(json => FakeRequest().withJsonBody(json))
        )

      val registrationDetails     = sample[RegistrationDetails]
      val registrationDetailsJson = Json.toJson(registrationDetails)
      val sapNumber               = sample[SapNumber]

      "return a bad request" when {

        "there is no JSON body in the request" in {
          val result = performAction(None)
          status(result) shouldBe BAD_REQUEST
        }

        "the JSON body cannot be parsed in the request" in {
          val result = performAction(Some(JsString("hi")))
          status(result) shouldBe BAD_REQUEST
        }

      }

      "return an internal server error" when {

        "there is a problem while trying to register without id" in {
          mockRegisterWithoutId(registrationDetails)(Left(Error("")))

          val result = performAction(Some(registrationDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return the subscription response" when {

        "registration is successful" in {
          inSequence {
            mockRegisterWithoutId(registrationDetails)(Right(sapNumber))
          }

          val result = performAction(Some(registrationDetailsJson))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(RegisteredWithoutId(sapNumber))
        }
      }

    }

  }
}
