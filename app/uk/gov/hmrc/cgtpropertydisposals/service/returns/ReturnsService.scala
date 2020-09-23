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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import java.time.{LocalDate, LocalDateTime}

import cats.data.EitherT
import cats.implicits.catsKernelStdOrderForString
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.codahale.metrics.Timer.Context
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.{ACCEPTED, NOT_FOUND, OK}
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.account.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.ReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesErrorResponse.SingleDesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{DesReturnDetails, DesSubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesErrorResponse, DesFinancialDataResponse, DesFinancialTransaction, DesFinancialTransactionItem}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{AgentReferenceNumber, CgtReference}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse.{DeltaCharge, ReturnCharge}
import uk.gov.hmrc.cgtpropertydisposals.models.returns._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.audit.{ReturnConfirmationEmailSentEvent, SubmitReturnEvent, SubmitReturnResponseEvent}
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService._
import uk.gov.hmrc.cgtpropertydisposals.service.returns.transformers.{ReturnSummaryListTransformerService, ReturnTransformerService}
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal
import scala.util.Try

@ImplementedBy(classOf[DefaultReturnsService])
trait ReturnsService {

  def submitReturn(returnRequest: SubmitReturnRequest, representeeDetails: Option[RepresenteeDetails])(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, Error, SubmitReturnResponse]

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[ReturnSummary]]

  def displayReturn(cgtReference: CgtReference, submissionId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, DisplayReturn]

}

@Singleton
class DefaultReturnsService @Inject() (
  returnsConnector: ReturnsConnector,
  financialDataConnector: FinancialDataConnector,
  returnTransformerService: ReturnTransformerService,
  returnSummaryListTransformerService: ReturnSummaryListTransformerService,
  draftReturnsService: DraftReturnsService,
  amendReturnsService: AmendReturnsService,
  emailConnector: EmailConnector,
  auditService: AuditService,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends ReturnsService
    with Logging {

  override def submitReturn(
    returnRequest: SubmitReturnRequest,
    representeeDetails: Option[RepresenteeDetails]
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, SubmitReturnResponse] = {
    val cgtReference                                   = returnRequest.subscribedDetails.cgtReference
    val taxYear                                        = returnRequest.completeReturn.fold(
      _.triageAnswers.taxYear,
      _.triageAnswers.disposalDate.taxYear,
      _.triageAnswers.disposalDate.taxYear,
      _.triageAnswers.taxYear,
      _.triageAnswers.disposalDate.taxYear
    )
    val (fromDate, toDate)                             = (taxYear.startDateInclusive, taxYear.endDateExclusive)
    val desSubmitReturnRequest: DesSubmitReturnRequest = DesSubmitReturnRequest(returnRequest, representeeDetails)
    for {
      _                  <- auditReturnBeforeSubmit(returnRequest, desSubmitReturnRequest)
      returnHttpResponse <- submitReturnAndAudit(returnRequest, desSubmitReturnRequest)
      _                  <- amendReturnsService.saveAmendedReturn(returnRequest).leftFlatMap { e =>
                              logger.warn(s"could not save recently amended return: $e")
                              EitherT.pure[Future, Error](())
                            }
      desResponse        <- EitherT.fromEither[Future](
                              returnHttpResponse.parseJSON[DesSubmitReturnResponse]().leftMap(Error(_))
                            )
      deltaCharge        <- deltaCharge(desResponse, cgtReference, fromDate, toDate)
      returnResponse     <- prepareSubmitReturnResponse(desResponse, deltaCharge)
      _                  <- sendEmailAndAudit(returnRequest, returnResponse).leftFlatMap { e =>
                              logger.warn("Could not send return submission confirmation email or audit event", e)
                              EitherT.pure[Future, Error](())
                            }
      _                  <- handleAmendedReturn(returnRequest)
    } yield returnResponse
  }

  private def deltaCharge(
    response: DesSubmitReturnResponse,
    cgtReference: CgtReference,
    fromDate: LocalDate,
    toDate: LocalDate
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[DeltaCharge]] =
    response.ppdReturnResponseDetails.chargeReference match {
      case Some(chargeReference) =>
        lazy val charge = for {
          desFinancialData <- getDesFinalcialData(cgtReference, fromDate, toDate)
          charge           <- EitherT.fromEither[Future](findDeltaCharge(desFinancialData.financialTransactions, chargeReference))
        } yield charge

        charge
      case _                     => EitherT.pure(None)
    }

  private def getDesFinalcialData(
    cgtReference: CgtReference,
    fromDate: LocalDate,
    toDate: LocalDate
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, DesFinancialDataResponse] = {
    val desFinancialData = {
      lazy val timer = metrics.financialDataTimer.time()
      financialDataConnector.getFinancialData(cgtReference, fromDate, toDate).subflatMap { response =>
        timer.close()

        if (response.status === OK)
          response.parseJSON[DesFinancialDataResponse]().leftMap(Error(_))
        else if (isNoFinancialDataResponse(response))
          Right(DesFinancialDataResponse(List.empty))
        else {
          metrics.financialDataErrorCounter.inc()
          Left(Error(s"call to get financial data came back with unexpected status ${response.status}"))
        }
      }
    }
    desFinancialData
  }

  private def findDeltaCharge(
    financialTransactions: List[DesFinancialTransaction],
    chargeReference: String
  ): Either[Error, Option[DeltaCharge]] =
    financialTransactions.filter(_.chargeReference === chargeReference) match {
      case _ :: Nil                            => Right(None)
      case transaction1 :: transaction2 :: Nil =>
        getReturnCharge(transaction1) -> getReturnCharge(transaction2) match {
          case (Some(r1), Some(r2)) =>
            if (r1.dueDate.isBefore(r2.dueDate)) Right(Some(DeltaCharge(r1, r2)))
            else Right(Some(DeltaCharge(r2, r1)))

          case _ =>
            Left(
              Error(
                s"Could not find return charges for both transactions in delta charge with charge reference $chargeReference: [transaction1 = $transaction1, transaction2 = $transaction2]"
              )
            )
        }
      case _                                   =>
        Left(
          Error(
            s"Could not find one or two transactions to look for delta charge  with charge reference $chargeReference: $financialTransactions"
          )
        )
    }

  private def getReturnCharge(transaction: DesFinancialTransaction): Option[ReturnCharge] =
    transaction.items.flatMap(_.collect {
      case DesFinancialTransactionItem(Some(amount), None, None, None, Some(dueDate)) =>
        ReturnCharge(transaction.chargeReference, AmountInPence.fromPounds(amount), dueDate)
    }.headOption)

  private def handleAmendedReturn(submitReturnRequest: SubmitReturnRequest): EitherT[Future, Error, Unit] = {
    def modifyDraftReturn(draftReturn: DraftReturn): Option[DraftReturn] =
      if (
        draftReturn.exemptionAndLossesAnswers.isEmpty && draftReturn.yearToDateLiabilityAnswers.isEmpty && draftReturn.supportingEvidenceAnswers.isEmpty
      )
        None
      else
        draftReturn match {
          case single: DraftSingleDisposalReturn                      =>
            Some(
              single.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
          case multiple: DraftMultipleDisposalsReturn                 =>
            Some(
              multiple.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
          case singleIndirect: DraftSingleIndirectDisposalReturn      =>
            Some(
              singleIndirect.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
          case multipleIndirect: DraftMultipleIndirectDisposalsReturn =>
            Some(
              multipleIndirect.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
          case singleMixedUse: DraftSingleMixedUseDisposalReturn      =>
            Some(
              singleMixedUse.copy(
                exemptionAndLossesAnswers = None,
                yearToDateLiabilityAnswers = None,
                supportingEvidenceAnswers = None
              )
            )
        }

    val cgtReference = submitReturnRequest.subscribedDetails.cgtReference
    if (submitReturnRequest.originalReturnFormBundleId.isEmpty)
      EitherT.pure(())
    else {
      import cats.instances.list._

      for {
        draftReturns        <- draftReturnsService.getDraftReturn(cgtReference)
        modifiedDraftReturns = draftReturns.foldLeft(List.empty[DraftReturn]) { (acc, curr) =>
                                 modifyDraftReturn(curr).fold(acc)(_ :: acc)
                               }
        _                   <- modifiedDraftReturns
                                 .map(d => draftReturnsService.saveDraftReturn(d, cgtReference))
                                 .sequence[EitherT[Future, Error, *], Unit]
      } yield ()
    }
  }

  private def auditReturnBeforeSubmit(
    returnRequest: SubmitReturnRequest,
    desSubmitReturnRequest: DesSubmitReturnRequest
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit] =
    EitherT.pure[Future, Error](
      auditService.sendEvent(
        "submitReturn",
        SubmitReturnEvent(
          desSubmitReturnRequest,
          returnRequest.subscribedDetails.cgtReference.value,
          returnRequest.agentReferenceNumber.map(_.value)
        ),
        "submit-return"
      )
    )

  private def submitReturnAndAudit(
    returnRequest: SubmitReturnRequest,
    desSubmitReturnRequest: DesSubmitReturnRequest
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, HttpResponse] = {
    val timer: Context = metrics.submitReturnTimer.time()
    returnsConnector
      .submit(
        returnRequest.subscribedDetails.cgtReference,
        desSubmitReturnRequest
      )
      .subflatMap { httpResponse =>
        timer.close()
        auditSubmitReturnResponse(
          httpResponse.status,
          httpResponse.body,
          desSubmitReturnRequest,
          returnRequest.subscribedDetails,
          returnRequest.agentReferenceNumber
        )

        if (httpResponse.status === OK)
          Right(httpResponse)
        else {
          metrics.submitReturnErrorCounter.inc()
          Left(Error(s"call to submit return came back with status ${httpResponse.status}"))
        }
      }
  }

  private def sendEmailAndAudit(
    returnRequest: SubmitReturnRequest,
    returnResponse: SubmitReturnResponse
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit] =
    for {
      _ <- sendReturnConfirmationEmail(returnRequest, returnResponse)
      _ <- auditSubscriptionConfirmationEmailSent(returnRequest, returnResponse)
    } yield ()

  private def sendReturnConfirmationEmail(
    returnRequest: SubmitReturnRequest,
    submitReturnResponse: SubmitReturnResponse
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubmitReturnResponse] = {
    val timer = metrics.submitReturnConfirmationEmailTimer.time()

    emailConnector
      .sendReturnSubmitConfirmationEmail(
        submitReturnResponse,
        returnRequest.subscribedDetails
      )
      .subflatMap { httpResponse =>
        timer.close()
        if (httpResponse.status === ACCEPTED)
          Right(submitReturnResponse)
        else {
          metrics.submitReturnConfirmationEmailErrorCounter.inc()
          Left(Error(s"Call to send confirmation email came back with status ${httpResponse.status}"))
        }
      }
  }

  private def auditSubscriptionConfirmationEmailSent(
    returnRequest: SubmitReturnRequest,
    submitReturnResponse: SubmitReturnResponse
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, SubmitReturnResponse] =
    EitherT
      .pure[Future, Error](
        auditService.sendEvent(
          "returnConfirmationEmailSent",
          ReturnConfirmationEmailSentEvent(
            returnRequest.subscribedDetails.emailAddress.value,
            returnRequest.subscribedDetails.cgtReference.value,
            submitReturnResponse.formBundleId
          ),
          "return-confirmation-email-sent"
        )
      )
      .map(_ => submitReturnResponse)

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, List[ReturnSummary]] = {
    lazy val desFinancialData = {
      val timer = metrics.financialDataTimer.time()
      financialDataConnector.getFinancialData(cgtReference, fromDate, toDate).subflatMap { response =>
        timer.close()

        if (response.status === OK)
          response.parseJSON[DesFinancialDataResponse]().leftMap(Error(_))
        else if (isNoFinancialDataResponse(response))
          Right(DesFinancialDataResponse(List.empty))
        else {
          metrics.financialDataErrorCounter.inc()
          Left(Error(s"call to get financial data came back with unexpected status ${response.status}"))
        }
      }
    }

    lazy val desReturnList: EitherT[Future, Error, DesListReturnsResponse] = {
      val timer = metrics.listReturnsTimer.time()
      returnsConnector.listReturns(cgtReference, fromDate, toDate).subflatMap { response =>
        timer.close()
        if (response.status === OK)
          response.parseJSON[DesListReturnsResponse]().leftMap(Error(_))
        else if (isNoReturnsResponse(response))
          Right(DesListReturnsResponse(List.empty))
        else {
          metrics.listReturnsErrorCounter.inc()
          Left(Error(s"call to list returns came back with unexpected status ${response.status}"))
        }
      }
    }

    lazy val recentlyAmendedReturnList: EitherT[Future, Error, List[SubmitReturnRequest]] =
      amendReturnsService.getAmendedReturn(cgtReference)

    for {
      desReturnList          <- desReturnList
      desFinancialData       <- if (desReturnList.returnList.nonEmpty) desFinancialData
                                else EitherT.pure(DesFinancialDataResponse(List.empty))
      recentlyAmendedReturns <- recentlyAmendedReturnList
      returnSummaries        <- EitherT.fromEither(
                                  if (desReturnList.returnList.nonEmpty)
                                    returnSummaryListTransformerService
                                      .toReturnSummaryList(
                                        desReturnList.returnList,
                                        desFinancialData.financialTransactions,
                                        recentlyAmendedReturns
                                      )
                                  else
                                    Right(List.empty)
                                )
    } yield returnSummaries
  }

  def isNoFinancialDataResponse(response: HttpResponse): Boolean = {
    lazy val hasNoReturnBody = response
      .parseJSON[DesErrorResponse]()
      .bimap(
        _ => false,
        _.hasError(SingleDesErrorResponse("NOT_FOUND", "The remote endpoint has indicated that no data can be found."))
      )
      .merge

    response.status === NOT_FOUND && hasNoReturnBody
  }

  private def isNoReturnsResponse(response: HttpResponse): Boolean = {
    lazy val hasNoReturnBody = response
      .parseJSON[DesErrorResponse]()
      .bimap(
        _ => false,
        _.hasError(
          SingleDesErrorResponse(
            "NOT_FOUND",
            "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."
          )
        )
      )
      .merge

    response.status === NOT_FOUND && hasNoReturnBody
  }

  def displayReturn(cgtReference: CgtReference, submissionId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, DisplayReturn] = {
    val timer = metrics.displayReturnTimer.time()
    returnsConnector.displayReturn(cgtReference, submissionId).subflatMap { response =>
      timer.close()
      if (response.status === OK)
        for {
          desReturn      <- response
                              .parseJSON[DesReturnDetails]()
                              .leftMap(Error(_))
          completeReturn <- returnTransformerService.toCompleteReturn(desReturn)
        } yield completeReturn
      else {
        metrics.displayReturnErrorCounter.inc()
        Left(Error(s"call to list returns came back with status ${response.status}"))
      }
    }
  }

  private def prepareSubmitReturnResponse(
    response: DesSubmitReturnResponse,
    deltaCharge: Option[DeltaCharge]
  ): EitherT[Future, Error, SubmitReturnResponse] =
    EitherT.fromEither[Future] {
      val charge = (
        response.ppdReturnResponseDetails.amount,
        response.ppdReturnResponseDetails.dueDate,
        response.processingDate,
        response.ppdReturnResponseDetails.chargeReference
      ) match {
        case (None, None, _, None)                                   => Right(None)
        case (Some(amount), _, _, _) if amount <= BigDecimal(0)      => Right(None)
        case (Some(amount), Some(dueDate), _, Some(chargeReference)) =>
          Right(
            Some(
              ReturnCharge(
                chargeReference,
                AmountInPence.fromPounds(amount),
                dueDate
              )
            )
          )
        case (amount, dueDate, processingDate, chargeReference)      =>
          Left(
            Error(
              s"Found some charge details but not all of them: (amount: $amount, dueDate: $dueDate, processing date: $processingDate, chargeReference: $chargeReference)"
            )
          )
      }
      charge.map(originalCharge =>
        SubmitReturnResponse(
          response.ppdReturnResponseDetails.formBundleNumber,
          response.processingDate,
          originalCharge,
          deltaCharge
        )
      )
    }

  private def auditSubmitReturnResponse(
    responseHttpStatus: Int,
    responseBody: String,
    desSubmitReturnRequest: DesSubmitReturnRequest,
    subscribedDetails: SubscribedDetails,
    agentReferenceNumber: Option[AgentReferenceNumber]
  )(implicit hc: HeaderCarrier, request: Request[_]): Unit = {
    val responseJson =
      Try(Json.parse(responseBody))
        .getOrElse(Json.parse(s"""{ "body" : "could not parse body as JSON: $responseBody" }"""))
    val requestJson  = Json.toJson(desSubmitReturnRequest)

    auditService.sendEvent(
      "submitReturnResponse",
      SubmitReturnResponseEvent(
        responseHttpStatus,
        responseJson,
        requestJson,
        subscribedDetails.name.fold(_.value, n => s"${n.firstName} ${n.lastName}"),
        agentReferenceNumber.map(_.value)
      ),
      "submit-return-response"
    )
  }

}

object DefaultReturnsService {

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

  final case class DesListReturnsResponse(
    returnList: List[DesReturnSummary]
  )

  final case class DesCharge(
    chargeDescription: String,
    dueDate: LocalDate,
    chargeReference: String
  )

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

  implicit val chargeFormat: OFormat[DesCharge]                             = Json.format
  implicit val returnFormat: OFormat[DesReturnSummary]                      = Json.format
  implicit val desListReturnResponseFormat: OFormat[DesListReturnsResponse] = Json.format

  implicit val ppdReturnResponseDetailsFormat: Format[DesSubmitReturnResponseDetails] =
    Json.format[DesSubmitReturnResponseDetails]
  implicit val desReturnResponseFormat: Format[DesSubmitReturnResponse]               = Json.format[DesSubmitReturnResponse]

}
