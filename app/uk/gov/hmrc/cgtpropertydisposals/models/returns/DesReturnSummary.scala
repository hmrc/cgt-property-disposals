/*
 * Copyright 2025 HM Revenue & Customs
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

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.des.AddressDetails

import java.time.{LocalDate, LocalDateTime}

final case class DesReturnSummary(
  submissionId: String,
  submissionDate: LocalDate,
  completionDate: LocalDate,
  lastUpdatedDate: Option[LocalDate],
  taxYear: String,
  propertyAddress: AddressDetails,
  totalCGTLiability: BigDecimal,
  charges: Option[List[DesCharge]]
)

final case class DesSubmitReturnResponseDetails(
  chargeReference: Option[String],
  amount: Option[BigDecimal],
  dueDate: Option[LocalDate],
  formBundleNumber: String
)

final case class DesSubmitReturnResponse(
  processingDate: LocalDateTime,
  ppdReturnResponseDetails: DesSubmitReturnResponseDetails
)

final case class DesListReturnsResponse(returnList: List[DesReturnSummary])

final case class DesCharge(chargeDescription: String, dueDate: LocalDate, chargeReference: String)

object DesReturnSummary {

  implicit val chargeFormat: OFormat[DesCharge]                             = Json.format
  implicit val returnFormat: OFormat[DesReturnSummary]                      = Json.format
  implicit val desListReturnResponseFormat: OFormat[DesListReturnsResponse] = Json.format

  implicit val ppdReturnResponseDetailsFormat: Format[DesSubmitReturnResponseDetails] =
    Json.format[DesSubmitReturnResponseDetails]

  implicit val desReturnResponseFormat: Format[DesSubmitReturnResponse] =
    Json.format[DesSubmitReturnResponse]

  val expiredMessage = "Amend deadline has passed"
}
