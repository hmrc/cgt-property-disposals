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

import com.typesafe.config.ConfigFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckPropertyChecks
import play.api.Configuration
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.{LatestTaxYearGoLiveDate, TaxYear, TaxYearConfig}
import uk.gov.hmrc.time.{TaxYear => HmrcTaxYear}

import java.time.LocalDate

class TaxYearServiceImplSpec extends AnyWordSpec with Matchers with ScalaCheckPropertyChecks {

  implicit def hint[A]: ProductHint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, KebabCase))

  private val currentTaxYear = HmrcTaxYear.current.startYear
  private val taxYearConfigs = (currentTaxYear to 2020 by -1).map { year =>
    sample[TaxYearConfig].copy(startYear = year)
  }

  private def config(flag: LocalDate) = {

    val taxYearsConfigObj                = ConfigWriter[List[TaxYearConfig]].to(taxYearConfigs.toList)
    val latestTaxYearGoLiveDateConfigObj = ConfigWriter[LatestTaxYearGoLiveDate].to(
      LatestTaxYearGoLiveDate(flag.getDayOfMonth, flag.getMonthValue, flag.getYear)
    )

    Configuration(
      ConfigFactory.parseString(
        s"""
           |latest-tax-year-go-live-date = ${latestTaxYearGoLiveDateConfigObj.render()}
           |tax-years = ${taxYearsConfigObj.render()}
           |""".stripMargin
      )
    )
  }

  private val service = new TaxYearServiceImpl(config(LocalDate.now.minusMonths(1)))

  private val service2 = new TaxYearServiceImpl(config(LocalDate.now))

  private val service3 = new TaxYearServiceImpl(config(LocalDate.now.plusMonths(1)))

  "TaxYearServiceImpl" when {

    "current date is after latestTaxYearGoLiveDate" when {

      "handling requests to get the tax year of a date" must {

        "return a tax year if config available for the year" in {
          taxYearConfigs.foreach { taxYearConfig =>
            service.getTaxYear(taxYearConfig.startDateInclusive)             shouldBe Some(taxYearConfig.as[TaxYear])
            service.getTaxYear(taxYearConfig.endDateExclusive.minusDays(1L)) shouldBe Some(taxYearConfig.as[TaxYear])
          }
        }

        "return nothing if a tax year not available in config" in {
          service.getTaxYear(taxYearConfigs.last.startDateInclusive.minusDays(1L)) shouldBe None
          service.getTaxYear(taxYearConfigs.head.endDateExclusive)                 shouldBe None
        }

      }

      "handling requests to get the available tax years" must {

        "return all available tax years" in {
          service.getAvailableTaxYears shouldBe taxYearConfigs.map(_.startYear)
        }

      }

    }

    "current date is equal to latestTaxYearGoLiveDate" when {
      "handling requests to get the tax year of a date" must {

        "return a tax year if config available for the year" in {
          taxYearConfigs.foreach { taxYearConfig =>
            service2.getTaxYear(taxYearConfig.startDateInclusive)             shouldBe Some(taxYearConfig.as[TaxYear])
            service2.getTaxYear(taxYearConfig.endDateExclusive.minusDays(1L)) shouldBe Some(taxYearConfig.as[TaxYear])
          }
        }

        "return nothing if a tax year not available in config" in {
          service2.getTaxYear(taxYearConfigs.last.startDateInclusive.minusDays(1L)) shouldBe None
          service2.getTaxYear(taxYearConfigs.head.endDateExclusive)                 shouldBe None
        }

      }

      "handling requests to get the available tax years" must {

        "return all available tax years" in {
          service.getAvailableTaxYears shouldBe taxYearConfigs.map(_.startYear)
        }

      }

    }

    "current date is before latestTaxYearGoLiveDate" when {

      "handling requests to get the tax year of a date" must {

        "return a tax year if config available for the year" in {
          taxYearConfigs.foreach { taxYearConfig =>
            if (taxYearConfig.startYear == HmrcTaxYear.current.startYear) {
              service3.getTaxYear(taxYearConfig.startDateInclusive)             shouldBe None
              service3.getTaxYear(taxYearConfig.endDateExclusive.minusDays(1L)) shouldBe None
            } else {
              service3.getTaxYear(taxYearConfig.startDateInclusive)             shouldBe Some(taxYearConfig.as[TaxYear])
              service3.getTaxYear(taxYearConfig.endDateExclusive.minusDays(1L)) shouldBe Some(
                taxYearConfig.as[TaxYear]
              )
            }
          }
        }

        "return nothing if a tax year not available in config" in {
          service3.getTaxYear(taxYearConfigs.last.startDateInclusive.minusDays(1L)) shouldBe None
          service3.getTaxYear(taxYearConfigs.head.endDateExclusive)                 shouldBe None
        }

      }

      "handling requests to get the available tax years" must {

        "return available previous tax years" in {
          service3.getAvailableTaxYears shouldBe taxYearConfigs.tail.map(_.startYear)
        }

      }

    }

  }

}
