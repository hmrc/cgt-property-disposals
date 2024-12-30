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
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.wordspec.AnyWordSpec
import play.api.Configuration
import pureconfig._
import pureconfig.configurable.localDateConfigConvert
import pureconfig.generic.auto._
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.{TaxYear, TaxYearConfig}
import uk.gov.hmrc.time.{TaxYear => HmrcTaxYear}

import java.time._
import java.time.format.DateTimeFormatter

class TaxYearServiceImplSpec extends AnyWordSpec with Matchers with TableDrivenPropertyChecks {
  private val currentTaxYear = HmrcTaxYear.current.startYear

  private val nextTaxYear                 = sample[TaxYearConfig].copy(startYear = currentTaxYear + 1, effectiveDate = None)
  private val currentTaxYearMidYearConfig =
    sample[TaxYearConfig].copy(startYear = currentTaxYear, effectiveDate = Some(LocalDate.of(currentTaxYear, 10, 31)))
  private val currentTaxYearConfig        = currentTaxYearMidYearConfig.copy(effectiveDate = None)
  private val currentTaxYearMinus1Config  =
    sample[TaxYearConfig].copy(startYear = currentTaxYear - 1, effectiveDate = None)
  private val currentTaxYearMinus2Config  =
    sample[TaxYearConfig].copy(startYear = currentTaxYear - 2, effectiveDate = None)
  private val currentTaxYearMinus3Config  =
    sample[TaxYearConfig].copy(startYear = currentTaxYear - 3, effectiveDate = None)
  private val currentTaxYearMinus4Config  =
    sample[TaxYearConfig].copy(startYear = currentTaxYear - 4, effectiveDate = None)

  private val taxYearConfigs = List(
    nextTaxYear,
    currentTaxYearMidYearConfig,
    currentTaxYearConfig,
    currentTaxYearMinus1Config,
    currentTaxYearMinus2Config,
    currentTaxYearMinus3Config,
    currentTaxYearMinus4Config
  )

  private val testData =
    Table(
      ("date", "expectedTaxYearConfig"),
      (nextTaxYear.startDateInclusive, nextTaxYear),
      (nextTaxYear.endDateExclusive.minusDays(1L), nextTaxYear),
      (currentTaxYearConfig.startDateInclusive, currentTaxYearConfig),
      (currentTaxYearConfig.endDateExclusive.minusDays(1L), currentTaxYearMidYearConfig),
      (currentTaxYearMinus1Config.startDateInclusive, currentTaxYearMinus1Config),
      (currentTaxYearMinus1Config.endDateExclusive.minusDays(1L), currentTaxYearMinus1Config),
      (currentTaxYearMinus2Config.startDateInclusive, currentTaxYearMinus2Config),
      (currentTaxYearMinus2Config.endDateExclusive.minusDays(1L), currentTaxYearMinus2Config),
      (currentTaxYearMinus3Config.startDateInclusive, currentTaxYearMinus3Config),
      (currentTaxYearMinus3Config.endDateExclusive.minusDays(1L), currentTaxYearMinus3Config),
      (currentTaxYearMinus4Config.startDateInclusive, currentTaxYearMinus4Config),
      (currentTaxYearMinus4Config.endDateExclusive.minusDays(1L), currentTaxYearMinus4Config)
    )

  implicit val localDateConvert: ConfigConvert[LocalDate] = localDateConfigConvert(DateTimeFormatter.ISO_DATE)

  private def config = {
    val taxYearsConfigObj = ConfigWriter[List[TaxYearConfig]].to(taxYearConfigs)

    Configuration(
      ConfigFactory.parseString(
        s"""
           |tax-years = ${taxYearsConfigObj.render()}
           |""".stripMargin
      )
    )
  }

  private val service = new TaxYearServiceImpl(
    config,
    Clock.fixed(ZonedDateTime.now.plusYears(1L).toInstant, ZoneId.of("UTC"))
  )

  private val service2 = new TaxYearServiceImpl(config, Clock.systemUTC())

  "TaxYearServiceImpl" when {
    "current date is now plus 1 year" when {
      "handling requests to get the tax year of a date" must {
        "return a tax year if config available for the year" in {
          forAll(testData) { (date: LocalDate, expectedTaxYearConfig: TaxYearConfig) =>
            service.getTaxYear(date) shouldBe Some(expectedTaxYearConfig.as[TaxYear])
          }
        }

        "return nothing if a tax year not available in config" in {
          service.getTaxYear(taxYearConfigs.last.startDateInclusive.minusDays(1L)) shouldBe None
          service.getTaxYear(taxYearConfigs.head.endDateExclusive)                 shouldBe None
        }
      }

      "handling requests to get the available tax years" must {
        "return all available tax years" in {
          service.getAvailableTaxYears shouldBe taxYearConfigs.map(_.startYear).distinct
        }
      }
    }

    "current date is now" when {
      "handling requests to get the tax year of a date" must {
        "return a tax year if config available for the year" in {
          forAll(testData) { (date: LocalDate, expectedTaxYearConfig: TaxYearConfig) =>
            if (expectedTaxYearConfig.startYear == currentTaxYear + 1) {
              service2.getTaxYear(date) shouldBe None
            } else {
              service2.getTaxYear(date) shouldBe Some(expectedTaxYearConfig.as[TaxYear])
            }
          }
        }

        "return nothing if a tax year not available in config" in {
          service2.getTaxYear(taxYearConfigs.last.startDateInclusive.minusDays(1L)) shouldBe None
          service2.getTaxYear(taxYearConfigs.head.endDateExclusive)                 shouldBe None
        }
      }

      "handling requests to get the available tax years" must {
        "return all available tax years" in {
          service2.getAvailableTaxYears shouldBe taxYearConfigs.drop(1).map(_.startYear).distinct
        }
      }
    }
  }
}
