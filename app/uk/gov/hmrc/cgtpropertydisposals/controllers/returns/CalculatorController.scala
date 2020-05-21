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

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculateCgtTaxDueRequest
import uk.gov.hmrc.cgtpropertydisposals.service.returns.CgtCalculationService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.Future

@Singleton
class CalculatorController @Inject() (
  authenticate: AuthenticateActions,
  calculatorService: CgtCalculationService,
  cc: ControllerComponents
) extends BackendController(cc)
    with Logging {

  def calculate: Action[JsValue] =
    authenticate(parse.json).async { implicit request =>
      withJsonBody[CalculateCgtTaxDueRequest] { calculationRequest =>
        val result = calculatorService.calculateTaxDue(
          calculationRequest.triageAnswers,
          calculationRequest.disposalDetails,
          calculationRequest.acquisitionDetails,
          calculationRequest.reliefDetails,
          calculationRequest.exemptionAndLosses,
          calculationRequest.estimatedIncome,
          calculationRequest.personalAllowance,
          calculationRequest.initialGainOrLoss,
          calculationRequest.isATrust
        )

        Future.successful(Ok(Json.toJson(result)))
      }
    }

}
