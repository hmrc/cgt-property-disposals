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

package uk.gov.hmrc.cgtpropertydisposals.models.des

import cats.Eq
import play.api.libs.json.{Format, Json, OFormat}

import java.time.LocalDate

final case class DesFinancialDataResponse(
  financialTransactions: List[DesFinancialTransaction]
)

object DesFinancialDataResponse {
  implicit val desFinancialDataResponseFormat: Format[DesFinancialDataResponse] = Json.format[DesFinancialDataResponse]
}

final case class DesFinancialTransaction(
  chargeReference: String,
  originalAmount: BigDecimal,
  items: Option[List[DesFinancialTransactionItem]]
)

object DesFinancialTransaction {
  implicit val financialTransactionFormat: Format[DesFinancialTransaction] = Json.format[DesFinancialTransaction]

  implicit val eq: Eq[DesFinancialTransaction] = Eq.fromUniversalEquals

}

final case class DesFinancialTransactionItem(
  amount: Option[BigDecimal],
  paymentMethod: Option[String],
  clearingDate: Option[LocalDate],
  clearingReason: Option[String],
  dueDate: Option[LocalDate]
)

object DesFinancialTransactionItem {
  implicit val format: OFormat[DesFinancialTransactionItem] = Json.format
}
