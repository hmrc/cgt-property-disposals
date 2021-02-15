/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear

class TaxYearServiceImplSpec extends WordSpec with Matchers {

  val taxYear =
    sample[TaxYear].copy(
      startDateInclusive = LocalDate.of(2020, 4, 6),
      endDateExclusive = LocalDate.of(2021, 4, 6)
    )

  val config  = Configuration(
    ConfigFactory.parseString(
      s"""
        | tax-years = [
        |  {
        |    start-year = ${taxYear.startDateInclusive.getYear}
        |    annual-exempt-amount {
        |      general              = ${taxYear.annualExemptAmountGeneral.inPounds()}
        |      non-vulnerable-trust = ${taxYear.annualExemptAmountNonVulnerableTrust.inPounds()}
        |    }
        |    personal-allowance = ${taxYear.personalAllowance.inPounds()}
        |    higher-income-personal-allowance-threshold = ${taxYear.higherIncomePersonalAllowanceThreshold.inPounds()}
        |
        |    max-personal-allowance = ${taxYear.maxPersonalAllowance.inPounds()}
        |    income-tax-higher-rate-threshold = ${taxYear.incomeTaxHigherRateThreshold.inPounds()}
        |    lettings-relief-max-threshold = ${taxYear.maxLettingsReliefAmount.inPounds()}
        |    cgt-rates {
        |      lower-band-residential      = ${taxYear.cgtRateLowerBandResidential}
        |      lower-band-non-residential  = ${taxYear.cgtRateLowerBandNonResidential}
        |      higher-band-residential     = ${taxYear.cgtRateHigherBandResidential}
        |      higher-band-non-residential = ${taxYear.cgtRateHigherBandNonResidential}
        |    }
        |  }
        | ]
        |""".stripMargin
    )
  )
  val service = new TaxYearServiceImpl(config)

  "TaxYearServiceImpl" when {

    "handling requests to get the tax year of a date" must {

      "return a tax year if one can be found" in {
        service.getTaxYear(taxYear.startDateInclusive)             shouldBe Some(taxYear)
        service.getTaxYear(taxYear.endDateExclusive.minusDays(1L)) shouldBe Some(taxYear)
      }

      "return nothing if a tax year cannot be found" in {
        service.getTaxYear(taxYear.startDateInclusive.minusDays(1L)) shouldBe None
        service.getTaxYear(taxYear.endDateExclusive)                 shouldBe None
      }

    }

  }

}
