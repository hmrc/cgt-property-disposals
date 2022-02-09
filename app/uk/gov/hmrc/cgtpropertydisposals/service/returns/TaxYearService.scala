/*
 * Copyright 2022 HM Revenue & Customs
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

import cats.syntax.order._
import configs.syntax._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.TaxYear
import uk.gov.hmrc.cgtpropertydisposals.models.LocalDateUtils._

@ImplementedBy(classOf[TaxYearServiceImpl])
trait TaxYearService {

  def getTaxYear(date: LocalDate): Option[TaxYear]

  def getAvailableTaxYears(): List[Int]

}

@Singleton
class TaxYearServiceImpl @Inject() (config: Configuration) extends TaxYearService {

  val latestTaxYearEnabled: Boolean =
    config.underlying.get[Boolean]("latestTaxYear.enabled").value

  val taxYears: List[TaxYear] = {
    val taxYearsList = config.underlying.get[List[TaxYear]]("tax-years").value
    if (latestTaxYearEnabled)
      taxYearsList
    else
      taxYearsList.drop(1)
  }

  override def getTaxYear(date: LocalDate): Option[TaxYear] =
    taxYears.find(t => date < t.endDateExclusive && date >= (t.startDateInclusive))

  override def getAvailableTaxYears(): List[Int] =
    taxYears.map(_.startDateInclusive.getYear).sorted(Ordering.Int.reverse)

}
