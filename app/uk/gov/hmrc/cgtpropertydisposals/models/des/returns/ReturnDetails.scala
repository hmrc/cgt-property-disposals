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
import uk.gov.hmrc.cgtpropertydisposals.models.returns.NumberOfProperties

final case class ValueAtTaxBandDetails(
  taxRate: BigDecimal,
  valueAtTaxRate: BigDecimal
)

final case class ReturnDetails(
  customerType: String,
  completionDate: LocalDate,
  isUKResident: Boolean,
  numberDisposals: NumberOfProperties,
  totalTaxableGain: BigDecimal,
  totalLiability: BigDecimal,
  totalYTDLiability: BigDecimal,
  estimate: Boolean,
  repayment: Boolean,
  attachmentUpload: Boolean,
  declaration: Boolean,
  countryResidence: Option[String],
  attachmentID: Option[String],
  entrepreneursRelief: Option[BigDecimal],
  valueAtTaxBandDetails: Option[List[ValueAtTaxBandDetails]],
  totalNetLoss: Option[BigDecimal],
  adjustedAmount: Option[BigDecimal]
)

object ReturnDetails {
  implicit val valueAtTaxBandDetailsForamt: OFormat[ValueAtTaxBandDetails] = Json.format[ValueAtTaxBandDetails]
  implicit val returnDetailsFormat: OFormat[ReturnDetails]                 = Json.format[ReturnDetails]
}
