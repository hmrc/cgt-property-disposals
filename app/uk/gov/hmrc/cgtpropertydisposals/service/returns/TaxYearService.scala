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

import cats.syntax.order._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.{Configuration, Logging}
import pureconfig.configurable.localDateConfigConvert
import pureconfig.generic.auto._
import pureconfig.{ConfigConvert, ConfigSource}
import uk.gov.hmrc.cgtpropertydisposals.models.LocalDateUtils._
import uk.gov.hmrc.cgtpropertydisposals.models.{LatestTaxYearGoLiveDate, TaxYear, TaxYearConfig}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.jdk.CollectionConverters._

@ImplementedBy(classOf[TaxYearServiceImpl])
trait TaxYearService {
  def getTaxYear(date: LocalDate): Option[TaxYear]

  def getAvailableTaxYears: List[Int]
}

@Singleton
class TaxYearServiceImpl @Inject() (config: Configuration) extends TaxYearService with Logging {
  implicit val localDateConvert: ConfigConvert[LocalDate] = localDateConfigConvert(DateTimeFormatter.ISO_DATE)

  private val taxYearLiveDate: LatestTaxYearGoLiveDate =
    ConfigSource
      .fromConfig(config.underlying.getConfig("latest-tax-year-go-live-date"))
      .loadOrThrow[LatestTaxYearGoLiveDate]

  private val latestTaxYearLiveDate: LocalDate =
    LocalDate.of(taxYearLiveDate.year, taxYearLiveDate.month, taxYearLiveDate.day)

  private def taxYearsConfig: List[TaxYearConfig] = {
    val taxYearsList = config.underlying
      .getConfigList("tax-years")
      .asScala
      .map(ConfigSource.fromConfig(_).loadOrThrow[TaxYearConfig])
      .toList

    if (LocalDate.now.isBefore(latestTaxYearLiveDate)) {
      taxYearsList.drop(2)
    } else {
      taxYearsList
    }
  }

  override def getTaxYear(date: LocalDate): Option[TaxYear] = {
    logger.error("getting tax year")
    val taxYears =
      taxYearsConfig.filter(t => date < t.endDateExclusive && date >= t.startDateInclusive).map(_.as[TaxYear])
    taxYears match {
      case head :: Nil  => Some(head)
      case head :: tail => getMidYearTaxYear(head :: tail, date)
      case _            => None
    }
  }

  private def getMidYearTaxYear(taxYears: List[TaxYear], date: LocalDate) = {
    taxYears.filter(_.effectiveDate.isDefined) match {
      case head :: Nil if head.effectiveDate.get.isBefore(date.plusDays(1)) => Some(head)
      case ::                                                    =>
        throw new RuntimeException(
          "Invalid tax band configuration. No support for multiple effective tax bands in a tax year"
        )
      case _                                                                => Some(taxYears.head)
    }
  }

  override def getAvailableTaxYears: List[Int] =
    taxYearsConfig.map(_.startDateInclusive.getYear).sorted(Ordering.Int.reverse).distinct
}
