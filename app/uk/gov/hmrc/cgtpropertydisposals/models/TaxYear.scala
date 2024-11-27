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

package uk.gov.hmrc.cgtpropertydisposals.models

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

import java.time.LocalDate

case class TaxYearConfig(
  startYear: Int,
  annualExemptAmount: AnnualExemptAmount,
  personalAllowance: Int,
  maxPersonalAllowance: Int,
  higherIncomePersonalAllowanceThreshold: Int,
  incomeTaxHigherRateThreshold: Int,
  cgtRates: CGTRates,
  lettingsReliefMaxThreshold: Int
) {
  def as[T](implicit f: TaxYearConfig => T): T = f(this)

  def startDateInclusive: LocalDate = LocalDate.of(startYear, 4, 6)
  def endDateExclusive: LocalDate   = LocalDate.of(startYear + 1, 4, 6)
}

case class AnnualExemptAmount(general: Int, nonVulnerableTrust: Int)

case class CGTRates(
  lowerBandResidential: BigDecimal,
  lowerBandNonResidential: BigDecimal,
  higherBandResidential: BigDecimal,
  higherBandNonResidential: BigDecimal
)

case class LatestTaxYearGoLiveDate(day: Int, month: Int, year: Int)

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
  implicit def taxYearMapper: TaxYearConfig => TaxYear = (taxYearConfig: TaxYearConfig) =>
    TaxYear(
      taxYearConfig.startDateInclusive,
      taxYearConfig.endDateExclusive,
      AmountInPence.fromPounds(taxYearConfig.annualExemptAmount.general),
      AmountInPence.fromPounds(taxYearConfig.annualExemptAmount.nonVulnerableTrust),
      AmountInPence.fromPounds(taxYearConfig.personalAllowance),
      AmountInPence.fromPounds(taxYearConfig.maxPersonalAllowance),
      AmountInPence.fromPounds(taxYearConfig.higherIncomePersonalAllowanceThreshold),
      AmountInPence.fromPounds(taxYearConfig.incomeTaxHigherRateThreshold),
      taxYearConfig.cgtRates.lowerBandResidential,
      taxYearConfig.cgtRates.lowerBandNonResidential,
      taxYearConfig.cgtRates.higherBandResidential,
      taxYearConfig.cgtRates.higherBandNonResidential,
      AmountInPence.fromPounds(taxYearConfig.lettingsReliefMaxThreshold)
    )

  implicit val format: OFormat[TaxYear] = Json.format
}
