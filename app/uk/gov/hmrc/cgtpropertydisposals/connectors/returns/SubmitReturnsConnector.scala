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
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnectorImpl.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalMethod._
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubmitReturnsConnectorImpl])
trait SubmitReturnsConnector {

  def submit(completeReturn: CompleteReturn)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class SubmitReturnsConnectorImpl @Inject() (http: HttpClient, val config: ServicesConfig)(implicit ec: ExecutionContext)
    extends SubmitReturnsConnector
    with DesConnector {

  override def submit(
    completeReturn: CompleteReturn
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] = {

    val baseUrl: String    = config.baseUrl("returns")
    val cgtReferenceNumber = completeReturn.cgtReference.value

    val returnUrl: String = s"$baseUrl/cgt-reference/$cgtReferenceNumber/return"

    val submitReturnRequest = SubmitReturnRequest(completeReturn)

    EitherT[Future, Error, HttpResponse](
      http
        .post(returnUrl, Json.toJson(submitReturnRequest), headers)(
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

  final case class SubmitReturnRequest(ppdReturnDetails: PPDReturnDetails)

  object SubmitReturnRequest {

    def apply(r: CompleteReturn): SubmitReturnRequest = {
      val calculatedTaxDue = r.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue

      val (taxableGain, netLoss) = {
        val value = r.exemptionsAndLossesDetails.taxableGainOrLoss.getOrElse(
          r.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.calculatedTaxDue.taxableGainOrNetLoss
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
        customerType          = r.subscriptionDetails.name.fold(_ => "trust", _ => "individual"),
        completionDate        = r.triageAnswers.completionDate.value,
        isUKResident          = r.triageAnswers.wasAUKResident,
        numberDisposals       = r.triageAnswers.numberOfProperties, // make into an Int, sys.error if more than one
        totalTaxableGain      = taxableGain,
        totalNetLoss          = netLoss,
        valueAtTaxBandDetails = valueAtTaxBandDetails,
        totalLiability        = r.yearToDateLiabilityAnswers.taxDue.inPounds,
        totalYTDLiability     = calculatedTaxDue.yearToDateLiability.inPounds, //TODO:totalYTDLiability=estimatedIncome
        estimate              = r.yearToDateLiabilityAnswers.hasEstimatedDetailsWithCalculatedTaxDue.hasEstimatedDetails,
        repayment             = false,
        attachmentUpload      = false, //TODO
        declaration           = true,
        adjustedAmount        = None,
        countryResidence      = None,
        attachmentID          = None,
        entrepreneursRelief   = None
      )

      val addressDetails = AddresssDetails(
        addressLine1 = r.propertyAddress.line1,
        addressLine2 = r.propertyAddress.line2,
        addressLine3 = r.propertyAddress.town,
        addressLine4 = r.propertyAddress.town,
        countryCode  = r.propertyAddress.countryCode,
        postalCode   = r.propertyAddress.postcode
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
        disposalDate     = r.triageAnswers.disposalDate.value,
        addressDetails   = addressDetails,
        assetType        = r.triageAnswers.assetType,
        acquisitionType  = r.acquisitionDetails.acquisitionMethod,
        landRegistry     = false,
        acquisitionPrice = r.acquisitionDetails.acquisitionPrice.inPounds,
        rebased          = r.acquisitionDetails.rebasedAcquisitionPrice.isDefined,
        disposalPrice    = r.disposalDetails.disposalPrice.inPounds,
        improvements     = r.acquisitionDetails.improvementCosts > AmountInPence.zero,
        percentOwned     = Some(r.disposalDetails.shareOfProperty.percentageValue),
        acquisitionDate  = Some(r.acquisitionDetails.acquisitionDate.value),
        rebasedAmount    = r.acquisitionDetails.rebasedAcquisitionPrice.map(_.inPounds),
        disposalType     = disposalMethod(r),
        improvementCosts = improvementCosts(r),
        acquisitionFees  = Some(r.acquisitionDetails.acquisitionFees.inPounds),
        disposalFees     = Some(r.disposalDetails.disposalFees.inPounds),
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
        inYearLoss      = r.exemptionsAndLossesDetails.inYearLosses > AmountInPence.zero,
        inYearLossUsed  = inYearLossUsed(r),
        preYearLoss     = r.exemptionsAndLossesDetails.previousYearsLosses > AmountInPence.zero,
        preYearLossUsed = preYearLossUsed(r)
      )

      def reliefs: Boolean =
        r.reliefDetails.privateResidentsRelief > AmountInPence.zero &
          r.reliefDetails.lettingsRelief > AmountInPence.zero &
          r.reliefDetails.otherReliefs.map(_.fold(_ => true, () => false)).isDefined

      val reliefDetails = ReliefDetails(
        reliefs            = reliefs,
        privateResRelief   = Some(r.reliefDetails.privateResidentsRelief.inPounds),
        lettingsReflief    = Some(r.reliefDetails.lettingsRelief.inPounds),
        giftHoldOverRelief = None,
        otherRelief        = r.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.name), () => None)),
        otherReliefAmount  = r.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.amount.inPounds), () => None))
      )

      val incomeAllowanceDetails = IncomeAllowanceDetails(
        annualExemption   = r.exemptionsAndLossesDetails.annualExemptAmount.inPounds,
        estimatedIncome   = Some(r.yearToDateLiabilityAnswers.estimatedIncome.inPounds),
        personalAllowance = r.yearToDateLiabilityAnswers.personalAllowance.map(_.inPounds),
        threshold         = Some(r.triageAnswers.disposalDate.taxYear.incomeTaxHigherRateThreshold.inPounds())
      )

      val ppdReturnDetails = PPDReturnDetails(
        returnType = CreateReturnType(
          source         = r.agentReferenceNumber.fold("self digital")(_ => "agent digital"),
          submissionType = SubmissionType.New
        ),
        returnDetails            = returnDetails,
        representedPersonDetails = None,
        disposalDetails          = disposalDetails,
        lossSummaryDetails       = lossSummaryDetails,
        incomeAllowanceDetails   = incomeAllowanceDetails,
        reliefDetails            = reliefDetails
      )

      SubmitReturnRequest(ppdReturnDetails)
    }

    implicit val submitReturnRequestFormat: OFormat[SubmitReturnRequest] = Json.format[SubmitReturnRequest]

  }

}
