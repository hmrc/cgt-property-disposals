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

/*
 * Copyright 2019 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http:www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.cgtpropertydisposals.controllers

import java.time.LocalDateTime

import cats.data.EitherT
import cats.instances.future._
import org.scalacheck.ScalacheckShapeless._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{AnyContent, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.{AuthenticateActions, AuthenticatedRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.SubscriptionDetails._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, RegistrationDetails, SapNumber, SubscribedDetails, SubscriptionDetails, SubscriptionResponse, TaxEnrolmentRequest, TelephoneNumber, sample}
import uk.gov.hmrc.cgtpropertydisposals.service.{RegisterWithoutIdService, SubscriptionService, TaxEnrolmentService}
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

  def mockSubscribe(expectedSubscriptionDetails: SubscriptionDetails)(response: Either[Error, SubscriptionResponse]) =
    (mockSubscriptionService
      .subscribe(_: SubscriptionDetails)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, *)
      .returning(EitherT(Future.successful(response)))

  def mockGetSubscription(cgtReference: CgtReference)(response: Either[Error, SubscribedDetails]): Unit =
    (mockSubscriptionService
      .getSubscription(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
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
            ))
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

      val subscriptionDetails     = sample[SubscriptionDetails]
      val subscriptionDetailsJson = Json.toJson(subscriptionDetails)
      val subscriptionResponse    = sample[SubscriptionResponse]
      val taxEnrolmentRequest = TaxEnrolmentRequest(
        Fake.user.ggCredId,
        subscriptionResponse.cgtReferenceNumber,
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
          mockSubscribe(subscriptionDetails)(Left(Error("oh no!")))

          val result = performAction(Some(subscriptionDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

        "subscription is not successful if the tax enrolment service returns an error" in {
          inSequence {
            mockSubscribe(subscriptionDetails)(Right(subscriptionResponse))
            mockAllocateEnrolment(taxEnrolmentRequest)(
              Left(Error("Failed to allocate tax enrolment"))
            )
          }

          val result = performAction(Some(subscriptionDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return the subscription response" when {

        "subscription is successful" in {
          inSequence {
            mockSubscribe(subscriptionDetails)(Right(subscriptionResponse))
            mockAllocateEnrolment(taxEnrolmentRequest)(Right(()))
          }

          val result = performAction(Some(subscriptionDetailsJson))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(subscriptionResponse)
        }
      }
    }

    "handling requests to register without id and subscribe" must {

      def performAction(requestBody: Option[JsValue]): Future[Result] =
        controller.registerWithoutIdAndSubscribe()(
          requestBody.fold[FakeRequest[AnyContent]](FakeRequest())(json => FakeRequest().withJsonBody(json))
        )

      val registrationDetails     = sample[RegistrationDetails]
      val registrationDetailsJson = Json.toJson(registrationDetails)
      val sapNumber               = sample[SapNumber]
      val subscriptionDetails     = SubscriptionDetails.fromRegistrationDetails(registrationDetails, sapNumber)
      val subscriptionResponse    = sample[SubscriptionResponse]
      val taxEnrolmentRequest = TaxEnrolmentRequest(
        Fake.user.ggCredId,
        subscriptionResponse.cgtReferenceNumber,
        subscriptionDetails.address,
        fixedTimestamp
      )

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

        "there is a problem while trying to subscribe" in {
          inSequence {
            mockRegisterWithoutId(registrationDetails)(Right(sapNumber))
            mockSubscribe(SubscriptionDetails.fromRegistrationDetails(registrationDetails, sapNumber))(
              Left(Error("oh no!"))
            )
          }

          val result = performAction(Some(registrationDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return the subscription response" when {

        "subscription is successful" in {
          inSequence {
            mockRegisterWithoutId(registrationDetails)(Right(sapNumber))
            mockSubscribe(SubscriptionDetails.fromRegistrationDetails(registrationDetails, sapNumber))(
              Right(subscriptionResponse)
            )
            mockAllocateEnrolment(taxEnrolmentRequest)(Right(()))
          }

          val result = performAction(Some(registrationDetailsJson))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(subscriptionResponse)
        }
      }

      "subscription is not successful if the tax enrolment service returns an error" in {
        inSequence {
          mockRegisterWithoutId(registrationDetails)(Right(sapNumber))
          mockSubscribe(SubscriptionDetails.fromRegistrationDetails(registrationDetails, sapNumber))(
            Right(subscriptionResponse)
          )
          mockAllocateEnrolment(taxEnrolmentRequest)(Left(Error("")))
        }

        val result = performAction(Some(registrationDetailsJson))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }

  }
}
