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

package uk.gov.hmrc.cgtpropertydisposals.controllers.account

import java.time.{LocalDate, LocalDateTime}

import akka.stream.Materializer
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Headers, Result}
import play.api.test.Helpers._
import play.api.test.Helpers.CONTENT_TYPE
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.service.account.FinancialDataService
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.AuditService
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.controllers.account.FinancialDataController.FinancialDataResponse
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.finance.FinancialTransaction
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class FinancialDataControllerSpec extends ControllerSpec {

  val financialDataService = mock[FinancialDataService]

  val auditService = mock[AuditService]

  implicit val headerCarrier = HeaderCarrier()

  def mockGetFinancialData(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    response: Either[Error, List[FinancialTransaction]]
  ) =
    (financialDataService
      .getFinancialData(_: CgtReference, _: LocalDate, _: LocalDate)(_: HeaderCarrier))
      .expects(cgtReference, fromDate, toDate, *)
      .returning(EitherT.fromEither[Future](response))

  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new FinancialDataController(
    authenticate         = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    financialDataService = financialDataService,
    auditService         = auditService,
    cc                   = Helpers.stubControllerComponents()
  )

  "FinancialDataController" when {

    "handling requests to get financial data" must {

      def performAction(cgtReference: CgtReference, fromDate: String, toDate: String): Future[Result] =
        controller.getFinancialData(cgtReference.value, fromDate, toDate)(request)

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

        "there is a problem getting the financial data" in {

          mockGetFinancialData(cgtReference, fromDate, toDate)(Left(Error("")))

          val result = performAction(cgtReference, fromDate.toString, toDate.toString)
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }

      }

      "return financial data" when {

        "financial data is successfully retrieved" in {
          val response = sample[FinancialDataResponse]
          mockGetFinancialData(cgtReference, fromDate, toDate)(Right(response.financialTransactions))

          val result = performAction(cgtReference, fromDate.toString, toDate.toString)

          status(result)        shouldBe 200
          contentAsJson(result) shouldBe Json.toJson(response)
        }

      }
    }

  }

}
