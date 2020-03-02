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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import java.time.LocalDate

import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.finance.{AmountInPence, Charge}

final case class ReturnSummary(
  submissionId: String,
  submissionDate: LocalDate,
  completionDate: LocalDate,
  lastUpdatedDate: Option[LocalDate],
  taxYear: String,
  totalCGTLiability: AmountInPence,
  totalOutstanding: AmountInPence,
  propertyAddress: UkAddress,
  charges: List[Charge]
)

object ReturnSummary {

  implicit val ukAddressFormat: OFormat[UkAddress] = Json.format

  implicit val format: OFormat[ReturnSummary] = Json.format

}