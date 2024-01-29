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

package uk.gov.hmrc.cgtpropertydisposals.models

import com.typesafe.config.Config
import play.api.ConfigLoader
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

import java.time.LocalDate
import scala.jdk.CollectionConverters.ListHasAsScala

final case class TaxYear(
  startDateInclusive: LocalDate,
  endDateExclusive: LocalDate,
  annualExemptAmountGeneral: AmountInPence,
  annualExemptAmountNonVulnerableTrust: AmountInPence,
  personalAllowance: AmountInPence,
  maxPersonalAllowance: AmountInPence,
  higherIncomePersonalAllowanceThreshold: AmountInPence,
  incomeTaxHigherRateThreshold: AmountInPence,
  cgtRateLowerBandResidential: BigDecimal,
  cgtRateLowerBandNonResidential: BigDecimal,
  cgtRateHigherBandResidential: BigDecimal,
  cgtRateHigherBandNonResidential: BigDecimal,
  maxLettingsReliefAmount: AmountInPence
)

object TaxYear {
  implicit val configLoader: ConfigLoader[List[TaxYear]] = (config: Config, key: String) =>
    config.getConfigList(key).asScala.toList.map { ty =>
      val startYear                              = ty.getInt("start-year")
      val annualExemptAmountGeneral              = ty.getInt("annual-exempt-amount.general")
      val annualExemptAmountNonVulnerableTrust   = ty.getInt(
        "annual-exempt-amount.non-vulnerable-trust"
      )
      val personalAllowance                      = ty.getInt("personal-allowance")
      val higherIncomePersonalAllowanceThreshold = ty.getInt(
        "higher-income-personal-allowance-threshold"
      )
      val maxPersonalAllowance                   = ty.getInt("max-personal-allowance")

      val incomeTaxHigherRateThreshold    = ty.getInt("income-tax-higher-rate-threshold")
      val cgtRateLowerBandResidential     = ty.getInt("cgt-rates.lower-band-residential")
      val cgtRateLowerBandNonResidential  = ty.getInt("cgt-rates.lower-band-non-residential")
      val cgtRateHigherBandResidential    = ty.getInt("cgt-rates.higher-band-residential")
      val cgtRateHigherBandNonResidential = ty.getInt("cgt-rates.higher-band-non-residential")
      val maxLettingsReliefAmount         = ty.getInt("lettings-relief-max-threshold")
      TaxYear(
        LocalDate.of(startYear, 4, 6),
        LocalDate.of(startYear + 1, 4, 6),
        AmountInPence.fromPounds(annualExemptAmountGeneral),
        AmountInPence.fromPounds(annualExemptAmountNonVulnerableTrust),
        AmountInPence.fromPounds(personalAllowance),
        AmountInPence.fromPounds(maxPersonalAllowance),
        AmountInPence.fromPounds(higherIncomePersonalAllowanceThreshold),
        AmountInPence.fromPounds(incomeTaxHigherRateThreshold),
        cgtRateLowerBandResidential,
        cgtRateLowerBandNonResidential,
        cgtRateHigherBandResidential,
        cgtRateHigherBandNonResidential,
        AmountInPence.fromPounds(maxLettingsReliefAmount)
      )
    }

  implicit val format: OFormat[TaxYear] = Json.format
}
