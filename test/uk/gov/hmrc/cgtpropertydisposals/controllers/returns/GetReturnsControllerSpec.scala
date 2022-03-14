/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import java.time.{LocalDate, LocalDateTime}

import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.Json
import play.api.mvc.Result
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{DisplayReturn, ListReturnsResponse, ReturnSummary}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.ReturnsService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class GetReturnsControllerSpec extends ControllerSpec {

  val mockReturnsService = mock[ReturnsService]

  def mockListReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    response: Either[Error, List[ReturnSummary]]
  ) =
    (mockReturnsService
      .listReturns(_: CgtReference, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(cgtReference, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  def mockDisplayReturn(cgtReference: CgtReference, submissionId: String)(
    response: Either[Error, DisplayReturn]
  ) =
    (mockReturnsService
      .displayReturn(_: CgtReference, _: String)(_: HeaderCarrier))
      .expects(cgtReference, submissionId, *)
      .returning(EitherT.fromEither[Future](response))

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    HeaderCarrier(),
    FakeRequest()
  )

  lazy val controller = new GetReturnsController(
    Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    mockReturnsService,
    stubControllerComponents()
  )

  "GetReturnsController" when {

    "handling request to list returns" must {

      def performAction(cgtReference: CgtReference, fromDate: String, toDate: String): Future[Result] =
        controller.listReturns(cgtReference.value, fromDate, toDate)(request)

      val cgtReference       = sample[CgtReference]
      val (fromDate, toDate) = LocalDate.of(2020, 1, 31) -> LocalDate.of(2020, 11, 2)

      "return an error" when {

        "the fromDate cannot be parsed" in {
          val result = performAction(cgtReference, "20203101", "2020-11-02")
          status(result) shouldBe BAD_REQUEST
        }

        "the toDate cannot be parsed" in {
          val result = performAction(cgtReference, "2020-01-31", "20203101")
          status(result) shouldBe BAD_REQUEST
        }

        "neither the fromDate or toDate can be parsed" in {
          val result = performAction(cgtReference, "20000101", "20203101")
          status(result) shouldBe BAD_REQUEST
        }

        "there is a problem getting the returns" in {
          mockListReturns(cgtReference, fromDate, toDate)(Left(Error("")))

          val result = performAction(cgtReference, "2020-01-31", "2020-11-02")
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return a list of returns" when {

        "the returns are successfully retrieved" in {
          val response = sample[ListReturnsResponse]
          mockListReturns(cgtReference, fromDate, toDate)(Right(response.returns))

          val result = performAction(cgtReference, "2020-01-31", "2020-11-02")
          status(result)        shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(response)
        }

      }

    }

    "handling requests to display a return" must {

      def performAction(cgtReference: CgtReference, submissionId: String): Future[Result] =
        controller.displayReturn(cgtReference.value, submissionId)(request)

      val cgtReference = sample[CgtReference]
      val submissionId = "id"

      "return an error" when {

        "there is an error getting the return" in {
          mockDisplayReturn(cgtReference, submissionId)(Left(Error("")))

          val result = performAction(cgtReference, submissionId)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }

      "return the json" when {

        "it is successfully retrieved" in {
          val response = sample[DisplayReturn]
          mockDisplayReturn(cgtReference, submissionId)(Right(response))

          val result = performAction(cgtReference, submissionId)
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(response)
        }

      }

    }

  }

}
