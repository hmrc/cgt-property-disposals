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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteSingleDisposalReturn

final case class IncomeAllowanceDetails(
  annualExemption: BigDecimal,
  estimatedIncome: Option[BigDecimal],
  personalAllowance: Option[BigDecimal],
  threshold: Option[BigDecimal]
)

object IncomeAllowanceDetails {

  def apply(c: CompleteSingleDisposalReturn): IncomeAllowanceDetails =
    IncomeAllowanceDetails(
      annualExemption   = c.exemptionsAndLossesDetails.annualExemptAmount.inPounds(),
      estimatedIncome   = c.yearToDateLiabilityAnswers.map(_.estimatedIncome.inPounds()).toOption,
      personalAllowance = c.yearToDateLiabilityAnswers.toOption.flatMap(_.personalAllowance.map(_.inPounds())),
      threshold         = Some(c.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
    )

  implicit val incomeAllowanceDetailsFormat: OFormat[IncomeAllowanceDetails] = Json.format[IncomeAllowanceDetails]

}
