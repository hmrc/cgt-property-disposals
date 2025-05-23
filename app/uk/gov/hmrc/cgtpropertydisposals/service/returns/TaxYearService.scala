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

import cats.syntax.order.*
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import pureconfig.*
import pureconfig.configurable.localDateConfigConvert
import pureconfig.generic.semiauto.*
import uk.gov.hmrc.cgtpropertydisposals.models.LocalDateUtils.*
import uk.gov.hmrc.cgtpropertydisposals.models.{TaxYear, TaxYearConfig}
import uk.gov.hmrc.time.TaxYear as HmrcTaxYear

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDate}
import scala.jdk.CollectionConverters.*

@ImplementedBy(classOf[TaxYearServiceImpl])
trait TaxYearService {
  def getTaxYear(date: LocalDate): Option[TaxYear]

  def getAvailableTaxYears: List[Int]
}

@Singleton
class TaxYearServiceImpl @Inject() (config: Configuration, clock: Clock) extends TaxYearService {
  implicit val localDateConvert: ConfigConvert[LocalDate]       = localDateConfigConvert(DateTimeFormatter.ISO_LOCAL_DATE)
  implicit val taxYearConfigReader: ConfigReader[TaxYearConfig] = deriveReader

  private def taxYearsConfig: List[TaxYearConfig] = {
    val taxYearsList = config.underlying
      .getConfigList("tax-years")
      .asScala
      .map(ConfigSource.fromConfig(_).loadOrThrow[TaxYearConfig])
      .toList

    taxYearsList.filter(_.startDateInclusive.isBefore(LocalDate.now(clock)))
  }

  override def getTaxYear(date: LocalDate): Option[TaxYear] = {

    val taxYearConfigsToAppend = getAvailableTaxYears
      .filter(year => !taxYearsConfig.exists(_.startYear == year))
      .map { year =>
        taxYearsConfig
          .filter(_.startYear < year)
          .head
          .copy(startYear = year, effectiveDate = None)
      }

    val supportedTaxYearsConfig =
      (taxYearConfigsToAppend ++ taxYearsConfig).filterNot(_.startYear < HmrcTaxYear.current.startYear - 4)

    val taxYears =
      supportedTaxYearsConfig.filter(t => date < t.endDateExclusive && date >= t.startDateInclusive).map(_.as[TaxYear])

    taxYears match {
      case head :: Nil => Some(head)
      case Nil         => None
      case _           => getMidYearTaxYear(taxYears, date)
    }
  }

  private def getMidYearTaxYear(taxYears: List[TaxYear], date: LocalDate) =
    taxYears.filter(_.effectiveDate.isDefined) match {
      case head :: Nil if head.effectiveDate.get.isBefore(date.plusDays(1)) => Some(head)
      case Nil | _ :: _ :: _                                                =>
        throw new RuntimeException(
          "Invalid tax band configuration. No support for multiple effective tax bands in a tax year"
        )
      case _                                                                => Some(taxYears.tail.head)
    }

  override def getAvailableTaxYears: List[Int] = {
    val currentTaxYear    = HmrcTaxYear.current.startYear
    val supportedTaxYears = (currentTaxYear to currentTaxYear - 4) by -1
    val configYears       = taxYearsConfig.map(_.startDateInclusive.getYear)

    (supportedTaxYears.toList ++ configYears).distinct
      .filterNot(_ < HmrcTaxYear.current.startYear - 4)
      .sorted(Ordering.Int.reverse)
  }
}
