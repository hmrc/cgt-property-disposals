/*
 * Copyright 2022 HM Revenue & Customs
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

import configs.ConfigReader
import configs.syntax._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

import java.time.LocalDate

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

  implicit val configs: ConfigReader[TaxYear] = ConfigReader.from { case (config, key) =>
    for {
      startYear                              <- config.get[Int](s"$key.start-year")
      annualExemptAmountGeneral              <- config.get[BigDecimal](s"$key.annual-exempt-amount.general")
      annualExemptAmountNonVulnerableTrust   <- config.get[BigDecimal](
                                                  s"$key.annual-exempt-amount.non-vulnerable-trust"
                                                )
      personalAllowance                      <- config.get[BigDecimal](s"$key.personal-allowance")
      higherIncomePersonalAllowanceThreshold <- config.get[BigDecimal](
                                                  s"$key.higher-income-personal-allowance-threshold"
                                                )
      maxPersonalAllowance                   <- config.get[BigDecimal](s"$key.max-personal-allowance")

      incomeTaxHigherRateThreshold    <- config.get[BigDecimal](s"$key.income-tax-higher-rate-threshold")
      cgtRateLowerBandResidential     <- config.get[BigDecimal](s"$key.cgt-rates.lower-band-residential")
      cgtRateLowerBandNonResidential  <- config.get[BigDecimal](s"$key.cgt-rates.lower-band-non-residential")
      cgtRateHigherBandResidential    <- config.get[BigDecimal](s"$key.cgt-rates.higher-band-residential")
      cgtRateHigherBandNonResidential <- config.get[BigDecimal](s"$key.cgt-rates.higher-band-non-residential")
      maxLettingsReliefAmount         <- config.get[BigDecimal](s"$key.lettings-relief-max-threshold")
    } yield TaxYear(
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
