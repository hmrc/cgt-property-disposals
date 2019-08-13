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

import akka.stream.Materializer
import cats.data.EitherT
import play.api.inject.bind
import play.api.inject.guice.GuiceableModule
import play.api.libs.json.{JsString, JsValue, Json}
import play.api.mvc.{RequestHeader, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, SubscriptionDetails, SubscriptionResponse, sample}
import uk.gov.hmrc.cgtpropertydisposals.service.SubscriptionService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class SubscriptionControllerSpec extends ControllerSpec {

  val mockService = mock[SubscriptionService]

  override val overrideBindings: List[GuiceableModule] =
    List(bind[SubscriptionService].toInstance(mockService))

  lazy val controller = instanceOf[SubscriptionController]

  def mockSubscribe(expectedSubscriptionDetails: SubscriptionDetails)(response: Either[Error, SubscriptionResponse]) =
    (mockService.subscribe(_: SubscriptionDetails)(_: HeaderCarrier))
      .expects(expectedSubscriptionDetails, *)
      .returning(EitherT(Future.successful(response)))

  "The SubscriptionController" when {

    "handling requests to subscribe" must {

      implicit lazy val mat: Materializer = fakeApplication.materializer

      val subscriptionDetails = sample[SubscriptionDetails]
      val subscriptionDetailsJson = Json.toJson(subscriptionDetails)

      "return a bad request" when {

        "there is no JSON body in the request" in {
          val result = controller.subscribe()(FakeRequest())
          status(result) shouldBe BAD_REQUEST
        }

        "the JSON body cannot be parsed in the request" in {
          val result = controller.subscribe()(FakeRequest().withJsonBody(JsString("hi")))
          status(result) shouldBe BAD_REQUEST
        }

      }

      "return an internal server error" when {

        "there is a problem while trying to subscribe" in {
          mockSubscribe(subscriptionDetails)(Left(Error("oh no!")))

          val result = controller.subscribe()(FakeRequest().withJsonBody(subscriptionDetailsJson))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return the subscription response" when {

        "subscription is successful" in {
          val subscriptionResponse = SubscriptionResponse("number")

          mockSubscribe(subscriptionDetails)(Right(subscriptionResponse))

          val result = controller.subscribe()(FakeRequest().withJsonBody(subscriptionDetailsJson))
          status(result) shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(subscriptionResponse)
        }
      }

    }

  }

}
