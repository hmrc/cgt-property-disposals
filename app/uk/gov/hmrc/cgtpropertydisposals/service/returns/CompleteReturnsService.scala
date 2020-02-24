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
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DefaultCompleteReturnsService._
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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
) extends CompleteReturnsService
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
        response
          .parseJSON[DesReturnResponse]()
          .map(r => prepareSubmitReturnResponse(r))
          .leftMap(Error(_))
      } else {
        metrics.submitReturnErrorCounter.inc()
        Left(Error(s"call to submit return came back with status ${response.status}"))
      }
    }
  }

  private def prepareSubmitReturnResponse(response: DesReturnResponse): SubmitReturnResponse = {
    val resDetails = response.ppdReturnResponseDetails
    SubmitReturnResponse(
      chargeReference = resDetails.chargeReference,
      amount          = AmountInPence.fromPounds(resDetails.amount),
      dueDate         = resDetails.dueDate,
      formBundleId    = resDetails.formBundleNumber
    )
  }

}

object DefaultCompleteReturnsService {

  final case class PPDReturnResponseDetails(
    chargeType: String,
    chargeReference: String,
    amount: Double,
    dueDate: LocalDate,
    formBundleNumber: String,
    cgtReferenceNumber: String
  )

  final case class DesReturnResponse(
    processingDate: LocalDateTime,
    ppdReturnResponseDetails: PPDReturnResponseDetails
  )

  implicit val ppdReturnResponseDetailsFormat: Format[PPDReturnResponseDetails] = Json.format[PPDReturnResponseDetails]
  implicit val desReturnResponseFormat: Format[DesReturnResponse]               = Json.format[DesReturnResponse]
}
