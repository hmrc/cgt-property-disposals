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
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteMultipleIndirectDisposalReturn, CompleteSingleDisposalReturn, CompleteSingleIndirectDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns._

final case class ReturnDetails(
  customerType: CustomerType,
  completionDate: LocalDate,
  isUKResident: Boolean,
  countryResidence: Option[String],
  numberDisposals: Int,
  totalTaxableGain: BigDecimal,
  totalLiability: BigDecimal,
  totalYTDLiability: BigDecimal,
  estimate: Boolean,
  repayment: Boolean,
  attachmentUpload: Boolean,
  declaration: Boolean,
  attachmentID: Option[String],
  entrepreneursRelief: Option[BigDecimal],
  valueAtTaxBandDetails: Option[List[ValueAtTaxBandDetails]],
  totalNetLoss: Option[BigDecimal],
  adjustedAmount: Option[BigDecimal]
)

object ReturnDetails {

  def apply(submitReturnRequest: SubmitReturnRequest): ReturnDetails =
    submitReturnRequest.completeReturn match {
      case s: CompleteSingleDisposalReturn           =>
        val calculatedTaxDue       = s.yearToDateLiabilityAnswers.map(_.calculatedTaxDue).toOption
        val taxDue                 = s.yearToDateLiabilityAnswers.fold(_.taxDue, _.taxDue).inPounds()
        val (taxableGain, netLoss) = getTaxableGainOrNetLoss(s)

        ReturnDetails(
          customerType = CustomerType(submitReturnRequest.subscribedDetails),
          completionDate = s.triageAnswers.completionDate.value,
          isUKResident = s.triageAnswers.countryOfResidence.isUk(),
          countryResidence = Some(s.triageAnswers.countryOfResidence).filter(!_.isUk()).map(_.code),
          numberDisposals = 1,
          totalTaxableGain = taxableGain,
          totalNetLoss = netLoss,
          valueAtTaxBandDetails = calculatedTaxDue.flatMap(ValueAtTaxBandDetails(_)),
          totalLiability = taxDue,
          totalYTDLiability = taxDue,
          estimate = s.yearToDateLiabilityAnswers.fold(_.hasEstimatedDetails, _.hasEstimatedDetails),
          repayment = false,
          attachmentUpload = s.hasAttachments,
          declaration = true,
          adjustedAmount = None,
          attachmentID = None,
          entrepreneursRelief = None
        )

      case m: CompleteMultipleDisposalsReturn        =>
        val taxDue                 = m.yearToDateLiabilityAnswers.taxDue.inPounds()
        val (taxableGain, netLoss) = getTaxableGainOrNetLoss(m)

        ReturnDetails(
          customerType = CustomerType(submitReturnRequest.subscribedDetails),
          completionDate = m.triageAnswers.completionDate.value,
          isUKResident = m.triageAnswers.countryOfResidence.isUk(),
          countryResidence = Some(m.triageAnswers.countryOfResidence).filter(!_.isUk()).map(_.code),
          numberDisposals = m.triageAnswers.numberOfProperties,
          totalTaxableGain = taxableGain,
          totalNetLoss = netLoss,
          valueAtTaxBandDetails = None,
          totalLiability = taxDue,
          totalYTDLiability = taxDue,
          estimate = m.yearToDateLiabilityAnswers.hasEstimatedDetails,
          repayment = false,
          attachmentUpload = m.hasAttachments,
          declaration = true,
          adjustedAmount = None,
          attachmentID = None,
          entrepreneursRelief = None
        )

      case s: CompleteSingleIndirectDisposalReturn   =>
        val taxDue                 = s.yearToDateLiabilityAnswers.taxDue.inPounds()
        val (taxableGain, netLoss) = getTaxableGainOrNetLoss(s)

        ReturnDetails(
          customerType = CustomerType(submitReturnRequest.subscribedDetails),
          completionDate = s.triageAnswers.completionDate.value,
          isUKResident = s.triageAnswers.countryOfResidence.isUk(),
          countryResidence = Some(s.triageAnswers.countryOfResidence).filter(!_.isUk()).map(_.code),
          numberDisposals = 1,
          totalTaxableGain = taxableGain,
          totalNetLoss = netLoss,
          valueAtTaxBandDetails = None,
          totalLiability = taxDue,
          totalYTDLiability = taxDue,
          estimate = s.yearToDateLiabilityAnswers.hasEstimatedDetails,
          repayment = false,
          attachmentUpload = s.hasAttachments,
          declaration = true,
          adjustedAmount = None,
          attachmentID = None,
          entrepreneursRelief = None
        )

      case m: CompleteMultipleIndirectDisposalReturn =>
        val taxDue                 = m.yearToDateLiabilityAnswers.taxDue.inPounds()
        val (taxableGain, netLoss) = getTaxableGainOrNetLoss(m)

        ReturnDetails(
          customerType = CustomerType(submitReturnRequest.subscribedDetails),
          completionDate = m.triageAnswers.completionDate.value,
          isUKResident = m.triageAnswers.countryOfResidence.isUk(),
          countryResidence = Some(m.triageAnswers.countryOfResidence).filter(!_.isUk()).map(_.code),
          numberDisposals = m.triageAnswers.numberOfProperties,
          totalTaxableGain = taxableGain,
          totalNetLoss = netLoss,
          valueAtTaxBandDetails = None,
          totalLiability = taxDue,
          totalYTDLiability = taxDue,
          estimate = m.yearToDateLiabilityAnswers.hasEstimatedDetails,
          repayment = false,
          attachmentUpload = m.hasAttachments,
          declaration = true,
          adjustedAmount = None,
          attachmentID = None,
          entrepreneursRelief = None
        )

    }

  private def getTaxableGainOrNetLoss(c: CompleteReturn): (BigDecimal, Option[BigDecimal]) = {
    val value = c match {
      case s: CompleteSingleDisposalReturn           =>
        s.yearToDateLiabilityAnswers.fold(
          _.taxableGainOrLoss,
          _.calculatedTaxDue.taxableGainOrNetLoss
        )

      case m: CompleteMultipleDisposalsReturn        =>
        m.yearToDateLiabilityAnswers.taxableGainOrLoss

      case s: CompleteSingleIndirectDisposalReturn   =>
        s.yearToDateLiabilityAnswers.taxableGainOrLoss

      case m: CompleteMultipleIndirectDisposalReturn =>
        m.yearToDateLiabilityAnswers.taxableGainOrLoss

    }

    if (value < AmountInPence.zero)
      AmountInPence.zero.inPounds() -> Some(value.inPounds().abs)
    else value.inPounds()           -> None
  }

  implicit val returnDetailsFormat: OFormat[ReturnDetails] = Json.format[ReturnDetails]
}
