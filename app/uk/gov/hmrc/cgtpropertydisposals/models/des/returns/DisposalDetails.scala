/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import java.time.LocalDate

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{AcquisitionMethod, AssetType}

final case class DisposalDetails(
  disposalDate: LocalDate,
  addressDetails: AddresssDetails,
  assetType: AssetType,
  acquisitionType: AcquisitionMethod,
  landRegistry: Boolean,
  acquisitionPrice: BigDecimal,
  rebased: Boolean,
  disposalPrice: BigDecimal,
  improvements: Boolean,
  percentOwned: Option[BigDecimal],
  acquisitionDate: Option[LocalDate],
  rebasedAmount: Option[BigDecimal],
  disposalType: Option[String],
  improvementCosts: Option[BigDecimal],
  acquisitionFees: Option[BigDecimal],
  disposalFees: Option[BigDecimal],
  initialGain: Option[BigDecimal],
  initialLoss: Option[BigDecimal]
)

object DisposalDetails {

  implicit val disposalDetailsFormat: OFormat[DisposalDetails] = Json.format[DisposalDetails]

}
