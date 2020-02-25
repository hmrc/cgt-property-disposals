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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.OK
import play.api.libs.json.{Format, Json}
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{Charge, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultCompleteReturnsService._
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigDecimal

@ImplementedBy(classOf[DefaultCompleteReturnsService])
trait CompleteReturnsService {

  def submitReturn(returnRequest: SubmitReturnRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubmitReturnResponse]

}

@Singleton
class DefaultCompleteReturnsService @Inject() (
  submitReturnsConnector: SubmitReturnsConnector,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends CompleteReturnsService
    with Logging {

  override def submitReturn(
    returnRequest: SubmitReturnRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubmitReturnResponse] =
    sendSubmitReturnRequest(returnRequest)

  private def sendSubmitReturnRequest(
    returnRequest: SubmitReturnRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubmitReturnResponse] = {
    val timer = metrics.submitReturnTimer.time()

    submitReturnsConnector.submit(returnRequest).subflatMap { response =>
      timer.close()
      if (response.status === OK) {
        for {
          desResponse <- response
                          .parseJSON[DesReturnResponse]()
                          .leftMap(Error(_))
          submitReturnResponse <- prepareSubmitReturnResponse(desResponse)
        } yield submitReturnResponse
      } else {
        metrics.submitReturnErrorCounter.inc()
        Left(Error(s"call to submit return came back with status ${response.status}"))
      }
    }
  }

  private def prepareSubmitReturnResponse(response: DesReturnResponse): Either[Error, SubmitReturnResponse] = {
    val charge = (
      response.ppdReturnResponseDetails.amount,
      response.ppdReturnResponseDetails.dueDate,
      response.ppdReturnResponseDetails.chargeReference
    ) match {
      case (None, None, None)                              => Right(None)
      case (Some(amount), _, _) if amount <= BigDecimal(0) => Right(None)
      case (Some(amount), Some(dueDate), Some(chargeReference)) =>
        Right(Some(Charge(chargeReference, AmountInPence.fromPounds(amount), dueDate)))
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

object DefaultCompleteReturnsService {

  final case class PPDReturnResponseDetails(
    chargeReference: Option[String],
    amount: Option[BigDecimal],
    dueDate: Option[LocalDate],
    formBundleNumber: String
  )

  final case class DesReturnResponse(
    processingDate: LocalDateTime,
    ppdReturnResponseDetails: PPDReturnResponseDetails
  )

  implicit val ppdReturnResponseDetailsFormat: Format[PPDReturnResponseDetails] = Json.format[PPDReturnResponseDetails]
  implicit val desReturnResponseFormat: Format[DesReturnResponse]               = Json.format[DesReturnResponse]
}
