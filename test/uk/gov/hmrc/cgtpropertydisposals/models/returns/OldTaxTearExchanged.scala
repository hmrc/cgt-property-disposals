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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import cats.Eq
import julienrf.json.derived
import play.api.libs.json.{Json, OFormat, Reads, Writes}

sealed trait OldTaxTearExchanged extends Product with Serializable

object OldTaxTearExchanged {

  case object TaxYear2021 extends OldTaxTearExchanged

  implicit val format: OFormat[OldTaxTearExchanged] = derived.oformat()
  implicit val eq: Eq[OldTaxTearExchanged]          = Eq.fromUniversalEquals
  //implicit val writes: Writes[OldTaxTearExchanged] = Json.writes[OldTaxTearExchanged]
  //implicit val reads: Reads[OldTaxTearExchanged]   = Json.reads[OldTaxTearExchanged]

}
