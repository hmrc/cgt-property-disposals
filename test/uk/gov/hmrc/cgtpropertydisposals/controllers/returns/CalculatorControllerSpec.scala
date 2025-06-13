/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatestplus.mockito.MockitoSugar.mock
import play.api.libs.json.{JsNumber, JsValue, Json}
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers.*
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.generators.FurtherReturnCalculationGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.generators.Generators.*
import uk.gov.hmrc.cgtpropertydisposals.models.generators.ReturnsGen.given
import uk.gov.hmrc.cgtpropertydisposals.models.returns.*
import uk.gov.hmrc.cgtpropertydisposals.service.returns.CgtCalculationService
import uk.gov.hmrc.http.HeaderCarrier

import java.time.LocalDateTime
import scala.concurrent.Future

class CalculatorControllerSpec extends ControllerSpec {

  private val mockCalculatorService = mock[CgtCalculationService]

  private def mockCalculateTaxDue(
    request: CalculateCgtTaxDueRequest
  )(response: CalculatedTaxDue) =
    when(
      mockCalculatorService
        .calculateTaxDue(
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any(),
          any()
        )
    ).thenReturn(response)

  private def mockCalculateTaxableGainOrLoss(
    request: TaxableGainOrLossCalculationRequest
  )(response: TaxableGainOrLossCalculation) =
    when(
      mockCalculatorService
        .calculateTaxableGainOrLoss(request)
    ).thenReturn(response)

  private def mockCalculateYearToDateLiability(
    request: YearToDateLiabilityCalculationRequest
  )(response: YearToDateLiabilityCalculation) =
    when(
      mockCalculatorService
        .calculateYearToDateLiability(request)
    ).thenReturn(response)

  val controller = new CalculatorController(
    Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    mockCalculatorService,
    stubControllerComponents()
  )

  def request(body: JsValue) =
    new AuthenticatedRequest(
      Fake.user,
      LocalDateTime.now(),
      HeaderCarrier(),
      FakeRequest().withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)
    )

  "CalculatorController" when {
    "handling requests to calculate tax due" must {
      def performAction(body: JsValue): Future[Result] =
        controller.calculateTaxDue()(request(body))

      "return a bad request" when {
        "the JSON body cannot be parsed" in {
          status(performAction(JsNumber(1))) shouldBe BAD_REQUEST
        }
      }

      "return an ok response" when {
        "the request body can be parsed and a calculation has been performed" in {
          val request          = sample[CalculateCgtTaxDueRequest]
          val calculatedTaxDue = sample[CalculatedTaxDue]

          mockCalculateTaxDue(request)(calculatedTaxDue)

          val result = performAction(Json.toJson(request))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(calculatedTaxDue)
        }
      }
    }

    "handling requests to calculate taxable gain or loss" must {
      def performAction(body: JsValue): Future[Result] =
        controller.calculateTaxableGainOrLoss()(request(body))

      "return a bad request" when {
        "the JSON body cannot be parsed" in {
          status(performAction(JsNumber(1))) shouldBe BAD_REQUEST
        }
      }

      "return an ok response" when {
        "the request body can be parsed and a calculation has been performed" in {
          val request     = sample[TaxableGainOrLossCalculationRequest]
          val calculation = sample[TaxableGainOrLossCalculation]

          mockCalculateTaxableGainOrLoss(request)(calculation)

          val result = performAction(Json.toJson(request))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(calculation)
        }
      }
    }

    "handling requests to calculate year to date liability" must {
      def performAction(body: JsValue): Future[Result] =
        controller.calculateYearToDateLiability()(request(body))

      "return a bad request" when {
        "the JSON body cannot be parsed" in {
          status(performAction(JsNumber(1))) shouldBe BAD_REQUEST
        }
      }

      "return an ok response" when {
        "the request body can be parsed and a calculation has been performed" in {
          val request     = sample[YearToDateLiabilityCalculationRequest]
          val calculation = sample[YearToDateLiabilityCalculation]

          mockCalculateYearToDateLiability(request)(calculation)

          val result = performAction(Json.toJson(request))
          status(result)        shouldBe OK
          contentAsJson(result) shouldBe Json.toJson(calculation)
        }
      }
    }
  }
}
