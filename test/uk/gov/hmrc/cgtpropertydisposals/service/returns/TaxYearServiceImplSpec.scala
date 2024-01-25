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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence

import java.time.LocalDate

class TaxYearServiceImplSpec extends AnyWordSpec with Matchers {
  private val taxYear2020 = TaxYear(
    startDateInclusive = LocalDate.of(2020, 4, 6),
    endDateExclusive = LocalDate.of(2021, 4, 6),
    annualExemptAmountGeneral = AmountInPence.fromPounds(12300),
    annualExemptAmountNonVulnerableTrust = AmountInPence.fromPounds(6150),
    personalAllowance = AmountInPence.fromPounds(12500),
    maxPersonalAllowance = AmountInPence.fromPounds(20000),
    higherIncomePersonalAllowanceThreshold = AmountInPence.fromPounds(100000),
    incomeTaxHigherRateThreshold = AmountInPence.fromPounds(37500),
    cgtRateLowerBandResidential = BigDecimal(18),
    cgtRateLowerBandNonResidential = BigDecimal(10),
    cgtRateHigherBandResidential = BigDecimal(28),
    cgtRateHigherBandNonResidential = BigDecimal(20),
    maxLettingsReliefAmount = AmountInPence.fromPounds(40000)
  )

  private val taxYear2021 =
    TaxYear(
      startDateInclusive = LocalDate.of(2021, 4, 6),
      endDateExclusive = LocalDate.of(2022, 4, 6),
      annualExemptAmountGeneral = AmountInPence.fromPounds(12300),
      annualExemptAmountNonVulnerableTrust = AmountInPence.fromPounds(6150),
      personalAllowance = AmountInPence.fromPounds(12570),
      maxPersonalAllowance = AmountInPence.fromPounds(20000),
      higherIncomePersonalAllowanceThreshold = AmountInPence.fromPounds(100000),
      incomeTaxHigherRateThreshold = AmountInPence.fromPounds(37700),
      cgtRateLowerBandResidential = BigDecimal(18),
      cgtRateLowerBandNonResidential = BigDecimal(10),
      cgtRateHigherBandResidential = BigDecimal(28),
      cgtRateHigherBandNonResidential = BigDecimal(20),
      maxLettingsReliefAmount = AmountInPence.fromPounds(40000)
    )

  private val taxYear2022 =
    TaxYear(
      startDateInclusive = LocalDate.of(2022, 4, 6),
      endDateExclusive = LocalDate.of(2023, 4, 6),
      annualExemptAmountGeneral = AmountInPence.fromPounds(12300),
      annualExemptAmountNonVulnerableTrust = AmountInPence.fromPounds(6150),
      personalAllowance = AmountInPence.fromPounds(12570),
      maxPersonalAllowance = AmountInPence.fromPounds(20000),
      higherIncomePersonalAllowanceThreshold = AmountInPence.fromPounds(100000),
      incomeTaxHigherRateThreshold = AmountInPence.fromPounds(37700),
      cgtRateLowerBandResidential = BigDecimal(18),
      cgtRateLowerBandNonResidential = BigDecimal(10),
      cgtRateHigherBandResidential = BigDecimal(28),
      cgtRateHigherBandNonResidential = BigDecimal(20),
      maxLettingsReliefAmount = AmountInPence.fromPounds(40000)
    )

  private val taxYear2023 =
    TaxYear(
      startDateInclusive = LocalDate.of(2023, 4, 6),
      endDateExclusive = LocalDate.of(2024, 4, 6),
      annualExemptAmountGeneral = AmountInPence.fromPounds(6000),
      annualExemptAmountNonVulnerableTrust = AmountInPence.fromPounds(6000),
      personalAllowance = AmountInPence.fromPounds(12570),
      maxPersonalAllowance = AmountInPence.fromPounds(20000),
      higherIncomePersonalAllowanceThreshold = AmountInPence.fromPounds(100000),
      incomeTaxHigherRateThreshold = AmountInPence.fromPounds(37700),
      cgtRateLowerBandResidential = BigDecimal(18),
      cgtRateLowerBandNonResidential = BigDecimal(10),
      cgtRateHigherBandResidential = BigDecimal(28),
      cgtRateHigherBandNonResidential = BigDecimal(20),
      maxLettingsReliefAmount = AmountInPence.fromPounds(40000)
    )

  private def config(flag: LocalDate) = Configuration(
    ConfigFactory
      .load("tax_years.conf")
      .withValue("latest-tax-year-go-live-date.day", ConfigValueFactory.fromAnyRef(flag.getDayOfMonth))
      .withValue("latest-tax-year-go-live-date.month", ConfigValueFactory.fromAnyRef(flag.getMonthValue))
      .withValue("latest-tax-year-go-live-date.year", ConfigValueFactory.fromAnyRef(flag.getYear))
  )

  private val service  = new TaxYearServiceImpl(config(LocalDate.now.minusMonths(1)))
  private val service2 = new TaxYearServiceImpl(config(LocalDate.now))
  private val service3 = new TaxYearServiceImpl(config(LocalDate.now.plusMonths(1)))

  "TaxYearServiceImpl" when {
    "current date is after latestTaxYearGoLiveDate" when {
      "handling requests to get the tax year of a date" must {
        "return a tax year 2020 if one can be found" in {
          service.getTaxYear(taxYear2020.startDateInclusive)             shouldBe Some(taxYear2020)
          service.getTaxYear(taxYear2020.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2020)
        }

        "return a tax year 2021 if one can be found" in {
          service.getTaxYear(taxYear2021.startDateInclusive)             shouldBe Some(taxYear2021)
          service.getTaxYear(taxYear2021.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2021)
        }

        "return a tax year 2022 if one can be found" in {
          service.getTaxYear(taxYear2022.startDateInclusive)             shouldBe Some(taxYear2022)
          service.getTaxYear(taxYear2022.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2022)
        }

        "return a tax year 2023 if one can be found" in {
          service.getTaxYear(taxYear2023.startDateInclusive)             shouldBe Some(taxYear2023)
          service.getTaxYear(taxYear2023.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2023)
        }

        "return nothing if a tax year cannot be found" in {
          service.getTaxYear(taxYear2020.startDateInclusive.minusDays(1L)) shouldBe None
          service.getTaxYear(taxYear2023.endDateExclusive)                 shouldBe None
        }
      }

      "handling requests to get the available tax years" must {
        "return available tax years 2020,2021,2022,2023" in {
          service.getAvailableTaxYears shouldBe List(2023, 2022, 2021, 2020)
        }
      }
    }

    "current date is equal to latestTaxYearGoLiveDate" when {
      "handling requests to get the tax year of a date" must {
        "return a tax year 2020 if one can be found" in {
          service2.getTaxYear(taxYear2020.startDateInclusive)             shouldBe Some(taxYear2020)
          service2.getTaxYear(taxYear2020.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2020)
        }

        "return a tax year 2021 if one can be found" in {
          service2.getTaxYear(taxYear2021.startDateInclusive)             shouldBe Some(taxYear2021)
          service2.getTaxYear(taxYear2021.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2021)
        }

        "return a tax year 2022 if one can be found" in {
          service2.getTaxYear(taxYear2022.startDateInclusive)             shouldBe Some(taxYear2022)
          service2.getTaxYear(taxYear2022.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2022)
        }

        "return a tax year 2023 if one can be found" in {
          service2.getTaxYear(taxYear2023.startDateInclusive)             shouldBe Some(taxYear2023)
          service2.getTaxYear(taxYear2023.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2023)
        }

        "return nothing if a tax year cannot be found" in {
          service2.getTaxYear(taxYear2020.startDateInclusive.minusDays(1L)) shouldBe None
          service2.getTaxYear(taxYear2023.endDateExclusive)                 shouldBe None
        }
      }

      "handling requests to get the available tax years" must {
        "return available tax years 2020,2021,2022,2023" in {
          service2.getAvailableTaxYears shouldBe List(2023, 2022, 2021, 2020)
        }
      }
    }

    "current date is before latestTaxYearGoLiveDate" when {
      "handling requests to get the tax year of a date" must {
        "return a tax year 2020 if one can be found" in {
          service3.getTaxYear(taxYear2020.startDateInclusive)             shouldBe Some(taxYear2020)
          service3.getTaxYear(taxYear2020.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2020)
        }

        "return a tax year 2021 if one can be found" in {
          service3.getTaxYear(taxYear2021.startDateInclusive)             shouldBe Some(taxYear2021)
          service3.getTaxYear(taxYear2021.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2021)
        }

        "return a tax year 2022 if one can be found" in {
          service3.getTaxYear(taxYear2022.startDateInclusive)             shouldBe Some(taxYear2022)
          service3.getTaxYear(taxYear2022.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear2022)
        }

        "return a tax year 2023 if one can be found" in {
          service3.getTaxYear(taxYear2023.startDateInclusive)             shouldBe None
          service3.getTaxYear(taxYear2023.endDateExclusive.minusDays(1L)) shouldBe None
        }

        "return nothing if a tax year cannot be found" in {
          service3.getTaxYear(taxYear2020.startDateInclusive.minusDays(1L)) shouldBe None
          service3.getTaxYear(taxYear2022.endDateExclusive)                 shouldBe None
        }
      }

      "handling requests to get the available tax years" must {
        "return available tax years 2020,2021,2022" in {
          service3.getAvailableTaxYears shouldBe List(2022, 2021, 2020)
        }
      }
    }
  }
}
