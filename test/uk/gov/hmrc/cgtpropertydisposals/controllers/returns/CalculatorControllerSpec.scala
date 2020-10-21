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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import java.time.LocalDateTime

import play.api.libs.json.{JsNumber, JsValue, Json}
import play.api.mvc.{Headers, Result}
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.AcquisitionDetailsAnswers.CompleteAcquisitionDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalDetailsAnswers.CompleteDisposalDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ReliefDetailsAnswers.CompleteReliefDetailsAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SingleDisposalTriageAnswers.CompleteSingleDisposalTriageAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CalculateCgtTaxDueRequest, CalculatedTaxDue, TaxableGainOrLossCalculation, TaxableGainOrLossCalculationRequest, YearToDateLiabilityCalculation, YearToDateLiabilityCalculationRequest}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.CgtCalculationService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class CalculatorControllerSpec extends ControllerSpec {

  val mockCalculatorService = mock[CgtCalculationService]

  def mockCalculateTaxDue(
    request: CalculateCgtTaxDueRequest
  )(response: CalculatedTaxDue) =
    (
      mockCalculatorService
        .calculateTaxDue(
          _: CompleteSingleDisposalTriageAnswers,
          _: UkAddress,
          _: CompleteDisposalDetailsAnswers,
          _: CompleteAcquisitionDetailsAnswers,
          _: CompleteReliefDetailsAnswers,
          _: CompleteExemptionAndLossesAnswers,
          _: AmountInPence,
          _: AmountInPence,
          _: Option[AmountInPence],
          _: Boolean
        )
      )
      .expects(
        request.triageAnswers,
        request.address,
        request.disposalDetails,
        request.acquisitionDetails,
        request.reliefDetails,
        request.exemptionAndLosses,
        request.estimatedIncome,
        request.personalAllowance,
        request.initialGainOrLoss,
        request.isATrust
      )
      .returning(response)

  def mockCalculateTaxableGainOrLoss(
    request: TaxableGainOrLossCalculationRequest
  )(response: TaxableGainOrLossCalculation) =
    (mockCalculatorService
      .calculateTaxableGainOrLoss(_: TaxableGainOrLossCalculationRequest))
      .expects(request)
      .returning(response)

  def mockCalculateYearToDateLiability(
    request: YearToDateLiabilityCalculationRequest
  )(response: YearToDateLiabilityCalculation) =
    (mockCalculatorService
      .calculateYearToDateLiability(_: YearToDateLiabilityCalculationRequest))
      .expects(request)
      .returning(response)

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
