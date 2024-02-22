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
import play.api.Configuration
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import uk.gov.hmrc.cgtpropertydisposals.models.LocalDateUtils._
import uk.gov.hmrc.cgtpropertydisposals.models.{LatestTaxYearGoLiveDate, TaxYear, TaxYearConfig}

import java.time.LocalDate
import scala.jdk.CollectionConverters._

@ImplementedBy(classOf[TaxYearServiceImpl])
trait TaxYearService {
  def getTaxYear(date: LocalDate): Option[TaxYear]

  def getAvailableTaxYears: List[Int]
}

@Singleton
class TaxYearServiceImpl @Inject() (config: Configuration) extends TaxYearService {
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
      taxYearsList.drop(1)
    } else {
      taxYearsList
    }
  }

  override def getTaxYear(date: LocalDate): Option[TaxYear] =
    taxYearsConfig.find(t => date < t.endDateExclusive && date >= t.startDateInclusive).map(_.as[TaxYear])

  override def getAvailableTaxYears: List[Int] =
    taxYearsConfig.map(_.startDateInclusive.getYear).sorted(Ordering.Int.reverse)
}
