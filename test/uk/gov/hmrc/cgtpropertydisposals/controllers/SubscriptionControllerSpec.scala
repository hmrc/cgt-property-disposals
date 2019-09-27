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

package uk.gov.hmrc.cgtpropertydisposals.controllers

import java.time.LocalDateTime

import akka.stream.Materializer
import cats.data.EitherT
import org.scalacheck.ScalacheckShapeless._
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, Json}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, SubscriptionDetails, SubscriptionResponse, TaxEnrolmentError, TaxEnrolmentRequest, sample}
import uk.gov.hmrc.cgtpropertydisposals.modules.TaxEnrolmentRetryProvider
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse.TaxEnrolmentCreated
import uk.gov.hmrc.cgtpropertydisposals.service.{SubscriptionService, TaxEnrolmentService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
class SubscriptionControllerSpec extends ControllerSpec with ScalaCheckDrivenPropertyChecks {

  val mockSubscriptionService       = mock[SubscriptionService]
  val mockTaxEnrolmentService       = mock[TaxEnrolmentService]
  val mockTaxEnrolmentRetryProvider = mock[TaxEnrolmentRetryProvider]

  val headerCarrier = HeaderCarrier()

  override val overrideBindings: List[GuiceableModule] =
    List(
      bind[SubscriptionService].toInstance(mockSubscriptionService),
      bind[TaxEnrolmentService].toInstance(mockTaxEnrolmentService),
      bind[TaxEnrolmentRetryProvider].toInstance(mockTaxEnrolmentRetryProvider)
    )

  lazy val controller = instanceOf[SubscriptionController]

  def mockSubscribe(expectedSubscriptionDetails: SubscriptionDetails)(response: Either[Error, SubscriptionResponse]) =
    (mockSubscriptionService
      .subscribe(_: SubscriptionDetails)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, *)
      .returning(EitherT(Future.successful(response)))

  def mockAllocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[TaxEnrolmentError, TaxEnrolmentResponse]
  ) =
    (mockTaxEnrolmentService
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT(Future.successful(response)))

  "The SubscriptionController" when {
    val controller = new SubscriptionController(
      authenticate              = Fake.login(Fake.user, LocalDateTime.of(2019, 9, 24, 15, 47, 20)),
      subscriptionService       = mockSubscriptionService,
      taxEnrolmentService       = mockTaxEnrolmentService,
      taxEnrolmentRetryProvider = mockTaxEnrolmentRetryProvider,
      cc                        = Helpers.stubControllerComponents()
    )
    "handling requests to subscribe" must {

      implicit lazy val mat: Materializer = fakeApplication.materializer

      "return a bad request" when {

        "there is no JSON body in the request" in {
          val request =
            new AuthenticatedRequest(
              Fake.user,
              LocalDateTime.now(),
              headerCarrier,
              FakeRequest().withJsonBody(JsString("hi"))
            )
          val result = controller.subscribe()(request)
          status(result) shouldBe BAD_REQUEST
        }

        "the JSON body cannot be parsed in the request" in {
          val request =
            new AuthenticatedRequest(
              Fake.user,
              LocalDateTime.now(),
              headerCarrier,
              FakeRequest().withJsonBody(JsString("hi"))
            )
          val result = controller.subscribe.apply(request)
          status(result) shouldBe BAD_REQUEST
        }

      }

      "return an internal server error" when {

        "there is a problem while trying to subscribe" in {
          val subscriptionDetails     = sample[SubscriptionDetails]
          val subscriptionDetailsJson = Json.toJson(subscriptionDetails)

          mockSubscribe(subscriptionDetails)(Left(Error("oh no!")))
          val request =
            new AuthenticatedRequest(
              Fake.user,
              LocalDateTime.now(),
              headerCarrier,
              FakeRequest().withJsonBody(subscriptionDetailsJson)
            )

          val result = controller.subscribe()(request)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return the subscription response" when {

        "subscription is successful" in {

          forAll { subscriptionDetails: SubscriptionDetails =>
            val subscriptionResponse = SubscriptionResponse("number")

            val fixedTimestamp = LocalDateTime.of(2019, 9, 24, 15, 47, 20)
            val cgtReference   = "number"
            val taxEnrolmentRequest = TaxEnrolmentRequest(
              "user-cred-id",
              cgtReference,
              subscriptionDetails.address,
              "InProgress",
              timestamp = fixedTimestamp
            )

            inSequence {
              mockSubscribe(subscriptionDetails)(Right(subscriptionResponse))
              mockAllocateEnrolmentToGroup(taxEnrolmentRequest)(
                Right(TaxEnrolmentCreated)
              )
            }
            val request =
              new AuthenticatedRequest(
                Fake.user,
                fixedTimestamp,
                headerCarrier,
                FakeRequest().withJsonBody(Json.toJson(subscriptionDetails))
              )

            val result = controller.subscribe()(request)
            status(result)        shouldBe OK
            contentAsJson(result) shouldBe Json.toJson(subscriptionResponse)
          }
        }
      }

      "subscription is successful even if allocation of enrolment fails" in {

        forAll { subscriptionDetails: SubscriptionDetails =>
          val subscriptionResponse = SubscriptionResponse("number")

          val fixedTimestamp = LocalDateTime.of(2019, 9, 24, 15, 47, 20)
          val cgtReference   = "number"
          val taxEnrolmentRequest = TaxEnrolmentRequest(
            "user-cred-id",
            cgtReference,
            subscriptionDetails.address,
            "InProgress",
            timestamp = fixedTimestamp
          )

          mockSubscribe(subscriptionDetails)(Right(subscriptionResponse))
          mockAllocateEnrolmentToGroup(taxEnrolmentRequest)(
            Left(TaxEnrolmentError(taxEnrolmentRequest))
          )
          val request =
            new AuthenticatedRequest(
              Fake.user,
              fixedTimestamp,
              headerCarrier,
              FakeRequest().withJsonBody(Json.toJson(subscriptionDetails))
            )

          val result = controller.subscribe()(request)
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(subscriptionResponse)
        }
      }
    }

  }
}
