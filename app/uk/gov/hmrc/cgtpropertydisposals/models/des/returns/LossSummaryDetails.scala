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

import cats.syntax.order._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.ExemptionAndLossesAnswers.CompleteExemptionAndLossesAnswers

final case class LossSummaryDetails(
  inYearLoss: Boolean,
  preYearLoss: Boolean,
  inYearLossUsed: Option[BigDecimal],
  preYearLossUsed: Option[BigDecimal]
)

object LossSummaryDetails {

  def apply(exemptionAndLossesAnswers: CompleteExemptionAndLossesAnswers): LossSummaryDetails = {
    val inYearLosses        = exemptionAndLossesAnswers.inYearLosses
    val previousYearsLosses = exemptionAndLossesAnswers.previousYearsLosses

    val preYearLossUsed =
      if (previousYearsLosses > AmountInPence.zero) Some(previousYearsLosses.inPounds())
      else None

    val inYearLossUsed =
      if (inYearLosses > AmountInPence.zero) Some(inYearLosses.inPounds())
      else None

    LossSummaryDetails(
      inYearLoss      = inYearLosses > AmountInPence.zero,
      inYearLossUsed  = inYearLossUsed,
      preYearLoss     = previousYearsLosses > AmountInPence.zero,
      preYearLossUsed = preYearLossUsed
    )
  }

  implicit val lossSummaryDetailsFormat: OFormat[LossSummaryDetails] = Json.format[LossSummaryDetails]

}
