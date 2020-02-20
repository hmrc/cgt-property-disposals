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

package uk.gov.hmrc.cgtpropertydisposals.connectors.returns

import cats.data.EitherT
import cats.syntax.order._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnectorImpl.DesSubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalMethod._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CalculatedTaxDue, CompleteReturn, SubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubmitReturnsConnectorImpl])
trait SubmitReturnsConnector {

  def submit(returnRequest: SubmitReturnRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class SubmitReturnsConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends SubmitReturnsConnector
    with DesConnector {

  override def submit(
    returnRequest: SubmitReturnRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {

    val baseUrl: String    = config.baseUrl("returns")
    val cgtReferenceNumber = returnRequest.subscribedDetails.cgtReference.value

    val returnUrl: String = s"$baseUrl/cgt-reference/$cgtReferenceNumber/return"

    val desSubmitReturnRequest = DesSubmitReturnRequest(returnRequest)

    EitherT[Future, Error, HttpResponse](
      http
        .post(returnUrl, Json.toJson(desSubmitReturnRequest), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }

}

object SubmitReturnsConnectorImpl {

  final case class DesSubmitReturnRequest(ppdReturnDetails: PPDReturnDetails)

  object DesSubmitReturnRequest {

    def apply(submitReturnRequest: SubmitReturnRequest): DesSubmitReturnRequest = {
      val c                = submitReturnRequest.completeReturn
      val calculatedTaxDue = c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue

      val (taxableGain, netLoss) = {
        val value = c.exemptionsAndLossesDetails.taxableGainOrLoss.getOrElse(
          c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue.taxableGainOrNetLoss
        )

        if (value < AmountInPence.zero)
          AmountInPence.zero.inPounds() -> Some(value.inPounds())
        else value.inPounds()           -> None
      }

      val (initialGain, initialLoss) = {
        val value = calculatedTaxDue.initialGainOrLoss

        if (value < AmountInPence.zero)
          Some(AmountInPence.zero.inPounds()) -> Some(value.inPounds())
        else Some(value.inPounds())           -> Some(AmountInPence.zero.inPounds())
      }

      val valueAtTaxBandDetails = calculatedTaxDue match {
        case _: CalculatedTaxDue.NonGainCalculatedTaxDue => None
        case g: CalculatedTaxDue.GainCalculatedTaxDue =>
          Some(
            List(
              ValueAtTaxBandDetails(g.taxDueAtLowerRate.taxRate, g.taxDueAtLowerRate.taxableAmount.inPounds()),
              ValueAtTaxBandDetails(g.taxDueAtHigherRate.taxRate, g.taxDueAtHigherRate.taxableAmount.inPounds())
            )
          )
      }

      val returnDetails = ReturnDetails(
        customerType          = submitReturnRequest.subscribedDetails.name.fold(_ => "trust", _ => "individual"),
        completionDate        = c.triageAnswers.completionDate.value,
        isUKResident          = c.triageAnswers.wasAUKResident,
        numberDisposals       = c.triageAnswers.numberOfProperties, // make into an Int, sys.error if more than one
        totalTaxableGain      = taxableGain,
        totalNetLoss          = netLoss,
        valueAtTaxBandDetails = valueAtTaxBandDetails,
        totalLiability        = c.yearToDateLiabilityAnswers.taxDue.inPounds,
        totalYTDLiability     = calculatedTaxDue.yearToDateLiability.inPounds, //TODO:totalYTDLiability=estimatedIncome
        estimate              = c.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.hasEstimatedDetails,
        repayment             = false,
        attachmentUpload      = false, //TODO
        declaration           = true,
        adjustedAmount        = None,
        countryResidence      = None,
        attachmentID          = None,
        entrepreneursRelief   = None
      )

      val addressDetails = AddresssDetails(
        addressLine1 = c.propertyAddress.line1,
        addressLine2 = c.propertyAddress.line2,
        addressLine3 = c.propertyAddress.town,
        addressLine4 = c.propertyAddress.town,
        countryCode  = c.propertyAddress.countryCode,
        postalCode   = c.propertyAddress.postcode
      )

      def improvementCosts(r: CompleteReturn): Option[BigDecimal] =
        if (r.acquisitionDetails.improvementCosts > AmountInPence.zero)
          Some(r.acquisitionDetails.improvementCosts.inPounds())
        else None

      def disposalMethod(r: CompleteReturn): Option[String] =
        r.triageAnswers.disposalMethod match {
          case Sold   => Some("Sold")
          case Gifted => Some("Gifted")
        }

      val disposalDetails = DisposalDetails(
        disposalDate     = c.triageAnswers.disposalDate.value,
        addressDetails   = addressDetails,
        assetType        = c.triageAnswers.assetType,
        acquisitionType  = c.acquisitionDetails.acquisitionMethod,
        landRegistry     = false,
        acquisitionPrice = c.acquisitionDetails.acquisitionPrice.inPounds,
        rebased          = c.acquisitionDetails.rebasedAcquisitionPrice.isDefined,
        disposalPrice    = c.disposalDetails.disposalPrice.inPounds,
        improvements     = c.acquisitionDetails.improvementCosts > AmountInPence.zero,
        percentOwned     = Some(c.disposalDetails.shareOfProperty.percentageValue),
        acquisitionDate  = Some(c.acquisitionDetails.acquisitionDate.value),
        rebasedAmount    = c.acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds),
        disposalType     = disposalMethod(c),
        improvementCosts = improvementCosts(c),
        acquisitionFees  = Some(c.acquisitionDetails.acquisitionFees.inPounds),
        disposalFees     = Some(c.disposalDetails.disposalFees.inPounds),
        initialGain      = initialGain,
        initialLoss      = initialLoss
      )

      def inYearLossUsed(r: CompleteReturn): Option[BigDecimal] =
        if (r.exemptionsAndLossesDetails.inYearLosses.inPounds > 0)
          Some(r.exemptionsAndLossesDetails.inYearLosses.inPounds)
        else None

      def preYearLossUsed(r: CompleteReturn): Option[BigDecimal] =
        if (r.exemptionsAndLossesDetails.inYearLosses.inPounds > 0)
          Some(r.exemptionsAndLossesDetails.inYearLosses.inPounds)
        else None

      val lossSummaryDetails = LossSummaryDetails(
        inYearLoss      = c.exemptionsAndLossesDetails.inYearLosses > AmountInPence.zero,
        inYearLossUsed  = inYearLossUsed(c),
        preYearLoss     = c.exemptionsAndLossesDetails.previousYearsLosses > AmountInPence.zero,
        preYearLossUsed = preYearLossUsed(c)
      )

      def reliefs: Boolean =
        c.reliefDetails.privateResidentsRelief > AmountInPence.zero &
          c.reliefDetails.lettingsRelief > AmountInPence.zero &
          c.reliefDetails.otherReliefs.map(_.fold(_ => true, () => false)).isDefined

      val reliefDetails = ReliefDetails(
        reliefs            = reliefs,
        privateResRelief   = Some(c.reliefDetails.privateResidentsRelief.inPounds),
        lettingsReflief    = Some(c.reliefDetails.lettingsRelief.inPounds),
        giftHoldOverRelief = None,
        otherRelief        = c.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.name), () => None)),
        otherReliefAmount  = c.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.amount.inPounds), () => None))
      )

      val incomeAllowanceDetails = IncomeAllowanceDetails(
        annualExemption   = c.exemptionsAndLossesDetails.annualExemptAmount.inPounds,
        estimatedIncome   = Some(c.yearToDateLiabilityAnswers.estimatedIncome.inPounds),
        personalAllowance = c.yearToDateLiabilityAnswers.personalAllowance.map(_.inPounds),
        threshold         = Some(c.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
      )

      val ppdReturnDetails = PPDReturnDetails(
        returnType = CreateReturnType(
          source         = submitReturnRequest.agentReferenceNumber.fold("self digital")(_ => "agent digital"),
          submissionType = SubmissionType.New
        ),
        returnDetails            = returnDetails,
        representedPersonDetails = None,
        disposalDetails          = disposalDetails,
        lossSummaryDetails       = lossSummaryDetails,
        incomeAllowanceDetails   = incomeAllowanceDetails,
        reliefDetails            = reliefDetails
      )

      DesSubmitReturnRequest(ppdReturnDetails)
    }

    implicit val desSubmitReturnRequestFormat: OFormat[DesSubmitReturnRequest] = Json.format[DesSubmitReturnRequest]

  }

}
