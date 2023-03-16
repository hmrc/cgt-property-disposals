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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.controllers.returns.TaxYearController.{AvailableTaxYearsResponse, TaxYearResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.service.returns.TaxYearService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.util.Try

@Singleton
class TaxYearController @Inject() (
  authenticate: AuthenticateActions,
  taxYearService: TaxYearService,
  cc: ControllerComponents
) extends BackendController(cc)
    with Logging {

  def taxYear(date: String): Action[AnyContent] =
    authenticate { _ =>
      Try(LocalDate.parse(date, DateTimeFormatter.ISO_DATE)).fold(
        { _ =>
          logger.warn(s"Could not parse date: $date")
          BadRequest
        },
        { d =>
          val maybeTaxYear = taxYearService.getTaxYear(d)
          Ok(Json.toJson(TaxYearResponse(maybeTaxYear)))
        }
      )
    }

  def availableTaxYears(): Action[AnyContent] =
    authenticate { _ =>
      val availableTaxYears = taxYearService.getAvailableTaxYears()
      Ok(Json.toJson(AvailableTaxYearsResponse(availableTaxYears)))
    }
}

object TaxYearController {

  final case class TaxYearResponse(value: Option[TaxYear])
  final case class AvailableTaxYearsResponse(value: List[Int])

  implicit val taxYearResponseFormat: OFormat[TaxYearResponse]                     = Json.format
  implicit val availableTaxYearsResponseFormat: OFormat[AvailableTaxYearsResponse] = Json.format

}
