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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

import scala.math.BigDecimal.RoundingMode

final case class TaxableAmountOfMoney(
  taxRate: BigDecimal,
  taxableAmount: AmountInPence
)

object TaxableAmountOfMoney {

  implicit class TaxableAmountOfMoneyOps(private val t: TaxableAmountOfMoney) extends AnyVal {

    def taxDue(): AmountInPence = {
      val result = (BigDecimal(t.taxableAmount.value.toString) * BigDecimal(t.taxRate.toString)) / BigDecimal("100")

      AmountInPence(result.setScale(0, RoundingMode.DOWN).longValue)
    }

  }

  implicit val format: OFormat[TaxableAmountOfMoney] = Json.format

}
