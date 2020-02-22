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
import cats.syntax.order._
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns._

final case class ReturnDetails(
  customerType: String,
  completionDate: LocalDate,
  isUKResident: Boolean,
  numberDisposals: Int,
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

  def apply(submitReturnRequest: SubmitReturnRequest): ReturnDetails = {
    val c                = submitReturnRequest.completeReturn
    val calculatedTaxDue = c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue

    ReturnDetails(
      customerType          = SubscribedDetails(submitReturnRequest),
      completionDate        = c.triageAnswers.completionDate.value,
      isUKResident          = c.triageAnswers.countryOfResidence.isUk(),
      numberDisposals       = NumberOfProperties(c),
      totalTaxableGain      = getTaxableGainOrNetLoss(c)._1,
      totalNetLoss          = getTaxableGainOrNetLoss(c)._2,
      valueAtTaxBandDetails = ValueAtTaxBandDetails(calculatedTaxDue),
      totalLiability        = c.yearToDateLiabilityAnswers.taxDue.inPounds,
      totalYTDLiability     = calculatedTaxDue.yearToDateLiability.inPounds,
      estimate              = c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.hasEstimatedDetails,
      repayment             = false,
      attachmentUpload      = false, //TODO
      declaration           = true,
      adjustedAmount        = None,
      countryResidence      = None,
      attachmentID          = None,
      entrepreneursRelief   = None
    )
  }

  private def getTaxableGainOrNetLoss(c: CompleteReturn): (BigDecimal, Option[BigDecimal]) = {
    val value = c.exemptionsAndLossesDetails.taxableGainOrLoss.getOrElse(
      c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue.taxableGainOrNetLoss
    )

    if (value < AmountInPence.zero)
      AmountInPence.zero.inPounds() -> Some(value.inPounds())
    else value.inPounds()           -> None
  }

  implicit val returnDetailsFormat: OFormat[ReturnDetails] = Json.format[ReturnDetails]
}
