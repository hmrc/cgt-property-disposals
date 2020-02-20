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

import java.time.LocalDate

import cats.data.EitherT
import cats.syntax.order._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, JsError, JsObject, JsResult, JsString, JsSuccess, JsValue, Json, OFormat, Writes}
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnectorImpl.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DisposalMethod._

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

  sealed trait SubmissionType extends Product with Serializable

  object SubmissionType {
    final case object Create extends SubmissionType

    final case object Amend extends SubmissionType

    implicit val format: Format[SubmissionType] = new Format[SubmissionType] {
      override def reads(json: JsValue): JsResult[SubmissionType] =
        json match {
          case JsString("Create") => JsSuccess(Create)
          case JsString("Amend")  => JsSuccess(Amend)
          case JsString(other)    => JsError(s"could not recognise submission type: $other")
          case other              => JsError(s"Expected type string for submission type but found $other")
        }

      override def writes(o: SubmissionType): JsValue = o match {
        case Create => JsString("Create")
        case Amend  => JsString("Amend")
      }
    }
  }

  sealed trait ReturnType extends Product with Serializable

  final case class CreateReturnType(
    source: String,
    submissionType: SubmissionType
  ) extends ReturnType

  final case class AmendReturnType(
    source: String,
    submissionType: SubmissionType,
    submissionID: String
  ) extends ReturnType

  final case class ValueAtTaxBandDetails(
    taxRate: BigDecimal,
    valueAtTaxRate: BigDecimal
  )

  final case class ReturnDetails(
    customerType: IndividualUserType,
    completionDate: CompletionDate,
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

  final case class AddresssDetails(
    addressLine1: String,
    addressLine2: Option[String],
    addressLine3: Option[String],
    addressLine4: Option[String],
    countryCode: String,
    postalCode: String
  )

  final case class RepresentedPersonDetails(
    capacitorPersonalRep: String,
    firstName: String,
    lastName: String,
    idType: String, //"ZCGT","NINO","UTR","TRN"
    idValue: String,
    dateOfBirth: Option[String],
    trustCessationDate: Option[String],
    trustTerminationDate: Option[String],
    addressDetails: Option[AddresssDetails],
    email: Option[String]
  )

  final case class DisposalDetails(
    disposalDate: DisposalDate, //TODO: LocalDate or DisposalDate??
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

  final case class LossSummaryDetails(
    inYearLoss: Boolean,
    preYearLoss: Boolean,
    inYearLossUsed: Option[BigDecimal],
    preYearLossUsed: Option[BigDecimal]
  )

  final case class IncomeAllowanceDetails(
    annualExemption: BigDecimal,
    estimatedIncome: Option[BigDecimal],
    personalAllowance: Option[BigDecimal],
    threshold: Option[BigDecimal]
  )

  final case class ReliefDetails(
    reliefs: Boolean,
    privateResRelief: Option[BigDecimal],
    lettingsReflief: Option[BigDecimal],
    giftHoldOverRelief: Option[BigDecimal],
    otherRelief: Option[String],
    otherReliefAmount: Option[BigDecimal]
  )

  final case class PPDReturnDetails(
    returnType: ReturnType,
    returnDetails: ReturnDetails,
    representedPersonDetails: Option[RepresentedPersonDetails],
    disposalDetails: DisposalDetails,
    lossSummaryDetails: LossSummaryDetails,
    incomeAllowanceDetails: IncomeAllowanceDetails,
    reliefDetails: ReliefDetails
  )

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
        customerType          = r.triageAnswers.individualUserType, // TODO: make string and check
        completionDate        = r.triageAnswers.completionDate,
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
        adjustedAmount        = None, // check,
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
        disposalDate     = r.triageAnswers.disposalDate,
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

      val reliefDetails = ReliefDetails(
        reliefs            = true, //TODO true if any reliefs are defined
        privateResRelief   = Some(r.reliefDetails.privateResidentsRelief.inPounds),
        lettingsReflief    = Some(r.reliefDetails.lettingsRelief.inPounds),
        giftHoldOverRelief = None, // TODO: check
        otherRelief        = r.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.name), () => None)),
        otherReliefAmount  = r.reliefDetails.otherReliefs.flatMap(_.fold(r => Some(r.amount.inPounds), () => None))
      )

      val incomeAllowanceDetails = IncomeAllowanceDetails(
        annualExemption   = r.exemptionsAndLossesDetails.annualExemptAmount.inPounds,
        estimatedIncome   = Some(r.yearToDateLiabilityAnswers.estimatedIncome.inPounds),
        personalAllowance = r.yearToDateLiabilityAnswers.personalAllowance.map(_.inPounds),
        threshold         = None // TODO: chehck
      )

      val ppdReturnDetails = PPDReturnDetails(
        returnType = CreateReturnType(
          source         = "Individual", //TODO: completeReturn.triageAnswers.individualUserType, check
          submissionType = SubmissionType.Create // TODO: chheck
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

    implicit val valueAtTaxBandDetailsForamt: OFormat[ValueAtTaxBandDetails] = Json.format[ValueAtTaxBandDetails]
    implicit val addresssDetailsFormat: OFormat[AddresssDetails]             = Json.format[AddresssDetails]

    implicit val createReturnTypeFormat: OFormat[CreateReturnType] = Json.format[CreateReturnType]
    implicit val amendReturnTypeFormat: OFormat[AmendReturnType]   = Json.format[AmendReturnType]
    implicit val returnTypeFormat: OFormat[ReturnType] = {
      OFormat[ReturnType](
        { json =>
          createReturnTypeFormat.reads(json).orElse(amendReturnTypeFormat.reads(json))
        }, { r: ReturnType =>
          r match {
            case c: CreateReturnType => createReturnTypeFormat.writes(c)
            case a: AmendReturnType  => amendReturnTypeFormat.writes(a)
          }
        }
      )
    }
    implicit val returnDetailsFormat: OFormat[ReturnDetails] = Json.format[ReturnDetails]
    implicit val representedPersonDetailsFormat: OFormat[RepresentedPersonDetails] =
      Json.format[RepresentedPersonDetails]
    implicit val disposalDetailsFormat: OFormat[DisposalDetails]               = Json.format[DisposalDetails]
    implicit val lossSummaryDetailsFormat: OFormat[LossSummaryDetails]         = Json.format[LossSummaryDetails]
    implicit val incomeAllowanceDetailsFormat: OFormat[IncomeAllowanceDetails] = Json.format[IncomeAllowanceDetails]
    implicit val reliefDetailsFormat: OFormat[ReliefDetails]                   = Json.format[ReliefDetails]

    implicit val ppdReturnDetailsFormat: OFormat[PPDReturnDetails]       = Json.format[PPDReturnDetails]
    implicit val submitReturnRequestFormat: OFormat[SubmitReturnRequest] = Json.format[SubmitReturnRequest]

  }

}
