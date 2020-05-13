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
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import com.codahale.metrics.Timer.Context
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.http.Status.{ACCEPTED, NOT_FOUND, OK}
import play.api.libs.json._
import play.api.mvc.Request
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.account.FinancialDataConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.ReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesErrorResponse.SingleDesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.{DesReturnDetails, DesSubmitReturnRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesErrorResponse, DesFinancialDataResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.AmountInPence
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{AgentReferenceNumber, CgtReference}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse.ReturnCharge
import uk.gov.hmrc.cgtpropertydisposals.models.returns.audit.{ReturnConfirmationEmailSentEvent, SubmitReturnEvent, SubmitReturnResponseEvent}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, RepresenteeDetails, ReturnSummary, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.AuditService
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

  def submitReturn(returnRequest: SubmitReturnRequest, representeeDetails: Option[RepresenteeDetails])(
    implicit hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, Error, SubmitReturnResponse]

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, List[ReturnSummary]]

  def displayReturn(cgtReference: CgtReference, submissionId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, CompleteReturn]

}

@Singleton
class DefaultReturnsService @Inject() (
  returnsConnector: ReturnsConnector,
  financialDataConnector: FinancialDataConnector,
  returnTransformerService: ReturnTransformerService,
  returnSummaryListTransformerService: ReturnSummaryListTransformerService,
  emailConnector: EmailConnector,
  auditService: AuditService,
  config: Configuration,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends ReturnsService
    with Logging {

  val desNonIsoCountryCodes: List[CountryCode] =
    config.underlying.get[List[CountryCode]]("des.non-iso-country-codes").value

  override def submitReturn(
    returnRequest: SubmitReturnRequest,
    representeeDetails: Option[RepresenteeDetails]
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, SubmitReturnResponse] = {
    val desSubmitReturnRequest = DesSubmitReturnRequest(returnRequest, representeeDetails)

    for {
      _                  <- auditReturnBeforeSubmit(returnRequest, desSubmitReturnRequest)
      returnHttpResponse <- submitReturnAndAudit(returnRequest, desSubmitReturnRequest)
      desResponse <- EitherT.fromEither[Future](
                      returnHttpResponse.parseJSON[DesSubmitReturnResponse]().leftMap(Error(_))
                    )
      returnResponse <- prepareSubmitReturnResponse(desResponse)
      _ <- sendEmailAndAudit(returnRequest, returnResponse).leftFlatMap { e =>
            logger.warn("Could not send return submission confirmation email or audit event", e)
            EitherT.pure[Future, Error](())
          }
    } yield returnResponse
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

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, List[ReturnSummary]] = {
    lazy val desFinancialData = {
      val timer = metrics.financialDataTimer.time()
      financialDataConnector.getFinancialData(cgtReference, fromDate, toDate).subflatMap { response =>
        timer.close()

        if (response.status === OK) {
          response.parseJSON[DesFinancialDataResponse]().leftMap(Error(_))
        } else if (isNoFinancialDataResponse(response)) {
          Right(DesFinancialDataResponse(List.empty))
        } else {
          metrics.financialDataErrorCounter.inc()
          Left(Error(s"call to get financial data came back with unexpected status ${response.status}"))
        }
      }
    }

    lazy val desReturnList = {
      val timer = metrics.listReturnsTimer.time()
      returnsConnector.listReturns(cgtReference, fromDate, toDate).subflatMap { response =>
        timer.close()
        if (response.status === OK) {
          response.parseJSON[DesListReturnsResponse]().leftMap(Error(_))
        } else if (isNoReturnsResponse(response)) {
          Right(DesListReturnsResponse(List.empty))
        } else {
          metrics.listReturnsErrorCounter.inc()
          Left(Error(s"call to list returns came back with unexpected status ${response.status}"))
        }
      }
    }

    for {
      desReturnList <- desReturnList
      desFinancialData <- if (desReturnList.returnList.nonEmpty) desFinancialData
                         else EitherT.pure(DesFinancialDataResponse(List.empty))
      returnSummaries <- EitherT.fromEither(
                          if (desReturnList.returnList.nonEmpty)
                            returnSummaryListTransformerService
                              .toReturnSummaryList(desReturnList.returnList, desFinancialData.financialTransactions)
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

  def displayReturn(cgtReference: CgtReference, submissionId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, CompleteReturn] = {
    val timer = metrics.displayReturnTimer.time()
    returnsConnector.displayReturn(cgtReference, submissionId).subflatMap { response =>
      timer.close()
      if (response.status === OK) {
        for {
          desReturn <- response
                        .parseJSON[DesReturnDetails]()
                        .leftMap(Error(_))
          completeReturn <- returnTransformerService.toCompleteReturn(desReturn)
        } yield completeReturn
      } else {
        metrics.displayReturnErrorCounter.inc()
        Left(Error(s"call to list returns came back with status ${response.status}"))
      }
    }
  }

  private def prepareSubmitReturnResponse(
    response: DesSubmitReturnResponse
  ): EitherT[Future, Error, SubmitReturnResponse] =
    EitherT.fromEither[Future] {
      val charge = (
        response.ppdReturnResponseDetails.amount,
        response.ppdReturnResponseDetails.dueDate,
        response.processingDate,
        response.ppdReturnResponseDetails.chargeReference
      ) match {
        case (None, None, _, None)                              => Right(None)
        case (Some(amount), _, _, _) if amount <= BigDecimal(0) => Right(None)
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
        case (amount, dueDate, processingDate, chargeReference) =>
          Left(
            Error(
              s"Found some charge details but not all of them: (amount: $amount, dueDate: $dueDate, processing date: $processingDate, chargeReference: $chargeReference)"
            )
          )
      }
      charge.map(SubmitReturnResponse(response.ppdReturnResponseDetails.formBundleNumber, response.processingDate, _))
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
    val requestJson = Json.toJson(desSubmitReturnRequest)

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
  implicit val desReturnResponseFormat: Format[DesSubmitReturnResponse] = Json.format[DesSubmitReturnResponse]

}
