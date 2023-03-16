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

import java.time.LocalDate

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear

class TaxYearServiceImplSpec extends AnyWordSpec with Matchers {

  val taxYear2020 =
    sample[TaxYear].copy(
      startDateInclusive = LocalDate.of(2020, 4, 6),
      endDateExclusive = LocalDate.of(2021, 4, 6)
    )

  val taxYear2021 =
    sample[TaxYear].copy(
      startDateInclusive = LocalDate.of(2021, 4, 6),
      endDateExclusive = LocalDate.of(2022, 4, 6)
    )

  val taxYear2022 =
    sample[TaxYear].copy(
      startDateInclusive = LocalDate.of(2022, 4, 6),
      endDateExclusive = LocalDate.of(2023, 4, 6)
    )

  def config(flag: LocalDate) = Configuration(
    ConfigFactory.parseString(
      s"""
         | latest-tax-year-go-live-date =
         | {
         | day = ${flag.getDayOfMonth}
         | month = ${flag.getMonthValue}
         | year = ${flag.getYear}
         | }
         | tax-years = [
         |   {
         |    start-year = ${taxYear2022.startDateInclusive.getYear}
         |    annual-exempt-amount {
         |      general              = ${taxYear2022.annualExemptAmountGeneral.inPounds()}
         |      non-vulnerable-trust = ${taxYear2022.annualExemptAmountNonVulnerableTrust.inPounds()}
         |    }
         |    personal-allowance = ${taxYear2022.personalAllowance.inPounds()}
         |    higher-income-personal-allowance-threshold = ${taxYear2022.higherIncomePersonalAllowanceThreshold
        .inPounds()}
         |
         |    max-personal-allowance = ${taxYear2022.maxPersonalAllowance.inPounds()}
         |    income-tax-higher-rate-threshold = ${taxYear2022.incomeTaxHigherRateThreshold.inPounds()}
         |    lettings-relief-max-threshold = ${taxYear2022.maxLettingsReliefAmount.inPounds()}
         |    cgt-rates {
         |      lower-band-residential      = ${taxYear2022.cgtRateLowerBandResidential}
         |      lower-band-non-residential  = ${taxYear2022.cgtRateLowerBandNonResidential}
         |      higher-band-residential     = ${taxYear2022.cgtRateHigherBandResidential}
         |      higher-band-non-residential = ${taxYear2022.cgtRateHigherBandNonResidential}
         |    }
         |  },
         |  {
         |    start-year = ${taxYear2021.startDateInclusive.getYear}
         |    annual-exempt-amount {
         |      general              = ${taxYear2021.annualExemptAmountGeneral.inPounds()}
         |      non-vulnerable-trust = ${taxYear2021.annualExemptAmountNonVulnerableTrust.inPounds()}
         |    }
         |    personal-allowance = ${taxYear2021.personalAllowance.inPounds()}
         |    higher-income-personal-allowance-threshold = ${taxYear2021.higherIncomePersonalAllowanceThreshold
        .inPounds()}
         |
         |    max-personal-allowance = ${taxYear2021.maxPersonalAllowance.inPounds()}
         |    income-tax-higher-rate-threshold = ${taxYear2021.incomeTaxHigherRateThreshold.inPounds()}
         |    lettings-relief-max-threshold = ${taxYear2021.maxLettingsReliefAmount.inPounds()}
         |    cgt-rates {
         |      lower-band-residential      = ${taxYear2021.cgtRateLowerBandResidential}
         |      lower-band-non-residential  = ${taxYear2021.cgtRateLowerBandNonResidential}
         |      higher-band-residential     = ${taxYear2021.cgtRateHigherBandResidential}
         |      higher-band-non-residential = ${taxYear2021.cgtRateHigherBandNonResidential}
         |    }
         |  },
         |    {
         |    start-year = ${taxYear2020.startDateInclusive.getYear}
         |    annual-exempt-amount {
         |      general              = ${taxYear2020.annualExemptAmountGeneral.inPounds()}
         |      non-vulnerable-trust = ${taxYear2020.annualExemptAmountNonVulnerableTrust.inPounds()}
         |    }
         |    personal-allowance = ${taxYear2020.personalAllowance.inPounds()}
         |    higher-income-personal-allowance-threshold = ${taxYear2020.higherIncomePersonalAllowanceThreshold
        .inPounds()}
         |
         |    max-personal-allowance = ${taxYear2020.maxPersonalAllowance.inPounds()}
         |    income-tax-higher-rate-threshold = ${taxYear2020.incomeTaxHigherRateThreshold.inPounds()}
         |    lettings-relief-max-threshold = ${taxYear2020.maxLettingsReliefAmount.inPounds()}
         |    cgt-rates {
         |      lower-band-residential      = ${taxYear2020.cgtRateLowerBandResidential}
         |      lower-band-non-residential  = ${taxYear2020.cgtRateLowerBandNonResidential}
         |      higher-band-residential     = ${taxYear2020.cgtRateHigherBandResidential}
         |      higher-band-non-residential = ${taxYear2020.cgtRateHigherBandNonResidential}
         |    }
         |  }
         | ]
         |""".stripMargin
    )
  )

  val service = new TaxYearServiceImpl(config(LocalDate.now.minusMonths(1)))

  val service2 = new TaxYearServiceImpl(config(LocalDate.now))

  val service3 = new TaxYearServiceImpl(config(LocalDate.now.plusMonths(1)))

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

        "return nothing if a tax year cannot be found" in {
          service.getTaxYear(taxYear2020.startDateInclusive.minusDays(1L)) shouldBe None
          service.getTaxYear(taxYear2022.endDateExclusive)                 shouldBe None
        }

      }

      "handling requests to get the available tax years" must {

        "return available tax years 2021,2020,2022" in {
          service.getAvailableTaxYears() shouldBe List(2022, 2021, 2020)
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

        "return nothing if a tax year cannot be found" in {
          service2.getTaxYear(taxYear2020.startDateInclusive.minusDays(1L)) shouldBe None
          service2.getTaxYear(taxYear2022.endDateExclusive)                 shouldBe None
        }

      }

      "handling requests to get the available tax years" must {

        "return available tax years 2021,2020,2022" in {
          service2.getAvailableTaxYears() shouldBe List(2022, 2021, 2020)
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
          service3.getTaxYear(taxYear2022.startDateInclusive)             shouldBe None
          service3.getTaxYear(taxYear2022.endDateExclusive.minusDays(1L)) shouldBe None
        }

        "return nothing if a tax year cannot be found" in {
          service3.getTaxYear(taxYear2020.startDateInclusive.minusDays(1L)) shouldBe None
          service3.getTaxYear(taxYear2021.endDateExclusive)                 shouldBe None
        }

      }

      "handling requests to get the available tax years" must {

        "return available tax years 2021,2020,2022" in {
          service3.getAvailableTaxYears() shouldBe List(2021, 2020)
        }

      }

    }

  }

}
