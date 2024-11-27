/*
 * Copyright 2024 HM Revenue & Customs
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
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CalculatedTaxDue

final case class ValueAtTaxBandDetails(
  taxRate: BigDecimal,
  valueAtTaxRate: BigDecimal
)

object ValueAtTaxBandDetails {

  def apply(calculatedTaxDue: CalculatedTaxDue): Option[List[ValueAtTaxBandDetails]] =
    calculatedTaxDue match {
      case _: CalculatedTaxDue.NonGainCalculatedTaxDue => None
      case g: CalculatedTaxDue.GainCalculatedTaxDue    =>
        Some(
          List(
            ValueAtTaxBandDetails(g.taxDueAtLowerRate.taxRate, g.taxDueAtLowerRate.taxableAmount.inPounds()),
            ValueAtTaxBandDetails(g.taxDueAtHigherRate.taxRate, g.taxDueAtHigherRate.taxableAmount.inPounds())
          )
        )
    }

  implicit val valueAtTaxBandDetailsForamt: OFormat[ValueAtTaxBandDetails] = Json.format[ValueAtTaxBandDetails]

}
