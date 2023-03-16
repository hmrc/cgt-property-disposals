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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import cats.Eq
import play.api.libs.json.{JsObject, JsPath, Json, OFormat, Reads}

final case class TaxYearExchanged(year: Int) extends Product with Serializable

object TaxYearExchanged {
  val taxYearExchangedBefore2020: TaxYearExchanged = TaxYearExchanged(-2020)

  val differentTaxYears: TaxYearExchanged = TaxYearExchanged(-1)

  private val oldReads: Reads[TaxYearExchanged] =
    (JsPath \ "TaxYear2022").read[JsObject].map(_ => TaxYearExchanged(year = 2022)) orElse
      (JsPath \ "TaxYear2021").read[JsObject].map(_ => TaxYearExchanged(year = 2021)) orElse
      (JsPath \ "TaxYear2020").read[JsObject].map(_ => TaxYearExchanged(year = 2020))

  implicit val reads: Reads[TaxYearExchanged] = Json.reads[TaxYearExchanged] orElse oldReads

  implicit val format: OFormat[TaxYearExchanged] = OFormat[TaxYearExchanged](reads, Json.writes[TaxYearExchanged])

  implicit val eq: Eq[TaxYearExchanged] = Eq.fromUniversalEquals

}
