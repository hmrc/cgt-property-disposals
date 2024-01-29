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
import uk.gov.hmrc.cgtpropertydisposals.models.LocalDateUtils._
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear

import java.time.LocalDate

@ImplementedBy(classOf[TaxYearServiceImpl])
trait TaxYearService {
  def getTaxYear(date: LocalDate): Option[TaxYear]

  def getAvailableTaxYears: List[Int]
}

@Singleton
class TaxYearServiceImpl @Inject() (config: Configuration) extends TaxYearService {
  private val taxYearLiveDate      = config.underlying.getConfig("latest-tax-year-go-live-date")
  private val taxYearLiveDateDay   = taxYearLiveDate.getInt("day")
  private val taxYearLiveDateMonth = taxYearLiveDate.getInt("month")
  private val taxYearLiveDateYear  = taxYearLiveDate.getInt("year")

  private val latestTaxYearLiveDate = LocalDate.of(taxYearLiveDateYear, taxYearLiveDateMonth, taxYearLiveDateDay)

  private def taxYears = {
    val taxYearsList = config.get[List[TaxYear]]("tax-years")
    if (LocalDate.now.isBefore(latestTaxYearLiveDate)) {
      taxYearsList.drop(1)
    } else {
      taxYearsList
    }
  }

  override def getTaxYear(date: LocalDate): Option[TaxYear] =
    taxYears.find(t => date < t.endDateExclusive && date >= t.startDateInclusive)

  override def getAvailableTaxYears: List[Int] =
    taxYears.map(_.startDateInclusive.getYear).sorted(Ordering.Int.reverse)
}
