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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{EitherT, NonEmptyList}
import cats.instances.either._
import cats.instances.future._
import cats.instances.int._
import cats.instances.list._
import cats.instances.string._
import cats.syntax.either._
import cats.syntax.eq._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.ReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesErrorResponse.SingleDesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, DesErrorResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.finance.{AmountInPence, Charge}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, ReturnSummary, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.des.returns.DesReturnDetails
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultReturnsService._
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal

@ImplementedBy(classOf[DefaultReturnsService])
trait ReturnsService {

  def submitReturn(returnRequest: SubmitReturnRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubmitReturnResponse]

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, List[ReturnSummary]]

  def displayReturn(cgtReference: CgtReference, submissionId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, CompleteReturn]

  def amendReturn(cgtReference: CgtReference, body: JsValue)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubmitReturnResponse]

}

@Singleton
class DefaultReturnsService @Inject() (
  returnsConnector: ReturnsConnector,
  returnTransformerService: ReturnTransformerService,
  config: Configuration,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends ReturnsService
    with Logging {

  val desNonIsoCountryCodes: List[CountryCode] =
    config.underlying.get[List[CountryCode]]("des.non-iso-country-codes").value

  override def submitReturn(
    returnRequest: SubmitReturnRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubmitReturnResponse] = {
    val timer = metrics.submitReturnTimer.time()

    returnsConnector.submit(returnRequest).subflatMap { response =>
      timer.close()
      if (response.status === OK) {
        for {
          desResponse <- response
                          .parseJSON[DesSubmitReturnResponse]()
                          .leftMap(Error(_))
          submitReturnResponse <- prepareSubmitReturnResponse(desResponse)
        } yield submitReturnResponse
      } else {
        metrics.submitReturnErrorCounter.inc()
        Left(Error(s"call to submit return came back with status ${response.status}"))
      }
    }
  }

  def listReturns(cgtReference: CgtReference, fromDate: LocalDate, toDate: LocalDate)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, List[ReturnSummary]] =
    returnsConnector.listReturns(cgtReference, fromDate, toDate).subflatMap { response =>
      if (response.status === OK) {
        for {
          desResponse <- response
                          .parseJSON[DesListReturnsResponse]()
                          .leftMap(Error(_))
          response <- listReturnResponse(desResponse)
        } yield response
      } else if (isNoReturnsResponse(response)) {
        Right(List.empty)
      } else {
        Left(Error(s"call to list returns came back with unexpected status ${response.status}"))
      }
    }

  private def isNoReturnsResponse(response: HttpResponse): Boolean = {
    def isNoReturnResponse(e: SingleDesErrorResponse) =
      e.code === "NOT_FOUND" &&
        e.reason === "The remote endpoint has indicated that the CGT reference is in use but no returns could be found."

    lazy val hasNoReturnBody = response
      .parseJSON[DesErrorResponse]()
      .bimap(
        _ => false,
        _.fold(isNoReturnResponse, _.failures.exists(isNoReturnResponse))
      )
      .merge

    response.status === NOT_FOUND && hasNoReturnBody
  }

  def displayReturn(cgtReference: CgtReference, submissionId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, CompleteReturn] =
    returnsConnector.displayReturn(cgtReference, submissionId).subflatMap { response =>
      if (response.status === OK) {
        for {
          desReturn <- response
                        .parseJSON[DesReturnDetails]()
                        .leftMap(Error(_))
          completeReturn <- returnTransformerService.toCompleteReturn(desReturn)
        } yield completeReturn
      } else {
        Left(Error(s"call to list returns came back with status ${response.status}"))
      }
    }

  def amendReturn(cgtReference: CgtReference, body: JsValue)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubmitReturnResponse] =
    returnsConnector
      .amendReturn(cgtReference, JsObject(Map("ppdReturnDetails" -> body)))
      .subflatMap { response =>
        if (response.status === OK) {
          for {
            desResponse <- response
                            .parseJSON[DesSubmitReturnResponse]()
                            .leftMap(Error(_))
            submitReturnResponse <- prepareSubmitReturnResponse(desResponse)
          } yield submitReturnResponse
        } else {
          Left(Error(s"call to submit return came back with status ${response.status}"))
        }
      }

  private def listReturnResponse(desListReturnsResponse: DesListReturnsResponse): Either[Error, List[ReturnSummary]] = {
    val returnSummaries = desListReturnsResponse.returnList.map { r =>
      val addressValidation = AddressDetails.fromDesAddressDetails(r.propertyAddress)(desNonIsoCountryCodes).andThen {
        case a: UkAddress    => Valid(a)
        case _: NonUkAddress => Invalid(NonEmptyList.one("Expected uk address but found non-uk address"))
      }

      addressValidation
        .bimap(
          e => Error(e.toList.mkString("; ")),
          address =>
            ReturnSummary(
              r.submissionId,
              r.submissionDate,
              r.completionDate,
              r.lastUpdatedDate,
              r.taxYear,
              AmountInPence.fromPounds(r.totalCGTLiability),
              AmountInPence.fromPounds(r.totalOutstanding),
              address,
              r.charges
                .getOrElse(List.empty)
                .map(c =>
                  Charge(c.chargeDescription, c.chargeReference, AmountInPence.fromPounds(c.chargeAmount), c.dueDate)
                )
            )
        )
        .toEither
    }

    returnSummaries.sequence[Either[Error, ?], ReturnSummary]
  }

  private def prepareSubmitReturnResponse(response: DesSubmitReturnResponse): Either[Error, SubmitReturnResponse] = {
    val charge = (
      response.ppdReturnResponseDetails.amount,
      response.ppdReturnResponseDetails.dueDate,
      response.ppdReturnResponseDetails.chargeReference
    ) match {
      case (None, None, None)                              => Right(None)
      case (Some(amount), _, _) if amount <= BigDecimal(0) => Right(None)
      case (Some(amount), Some(dueDate), Some(chargeReference)) =>
        Right(Some(Charge("charge from return submission", chargeReference, AmountInPence.fromPounds(amount), dueDate)))
      case (amount, dueDate, chargeReference) =>
        Left(
          Error(
            s"Found some charge details but not all of them: (amount: $amount, dueDate: $dueDate, chargeReference: $chargeReference)"
          )
        )
    }
    charge.map(SubmitReturnResponse(response.ppdReturnResponseDetails.formBundleNumber, _))
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
    chargeAmount: BigDecimal,
    dueDate: LocalDate,
    chargeReference: String
  )

  final case class DesReturnSummary(
    submissionId: String,
    submissionDate: LocalDate,
    completionDate: LocalDate,
    lastUpdatedDate: Option[LocalDate],
    taxYear: String,
    status: Option[String],
    totalCGTLiability: BigDecimal,
    totalOutstanding: BigDecimal,
    propertyAddress: AddressDetails,
    charges: Option[List[DesCharge]]
  )

  implicit val chargeFormat: OFormat[DesCharge]                             = Json.format
  implicit val returnFormat: OFormat[DesReturnSummary]                      = Json.format
  implicit val desListReturnResponseFormat: OFormat[DesListReturnsResponse] = Json.format

  implicit val ppdReturnResponseDetailsFormat: Format[DesSubmitReturnResponseDetails] =
    Json.format[DesSubmitReturnResponseDetails]
  implicit val desReturnResponseFormat: Format[DesSubmitReturnResponse] = Json.format[DesSubmitReturnResponse]

}