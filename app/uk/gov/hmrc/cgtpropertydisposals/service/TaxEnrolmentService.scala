/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.JsString
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[TaxEnrolmentServiceImpl])
trait TaxEnrolmentService {
  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit]
  def hasCgtSubscription(ggCredId: String)(implicit hc: HeaderCarrier): EitherT[Future, Error, Boolean]
}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRepository: TaxEnrolmentRepository
) extends TaxEnrolmentService
    with Logging {

  def makeTaxEnrolmentCall(
    taxEnrolmentRequest: TaxEnrolmentRequest
  )(implicit hc: HeaderCarrier): Future[HttpResponse] =
    taxEnrolmentConnector
      .allocateEnrolmentToGroup(taxEnrolmentRequest)
      .value
      .map {
        case Left(error)         => HttpResponse(999, Some(JsString(error.toString)))
        case Right(httpResponse) => httpResponse
      }

  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] =
    for {
      httpResponse <- EitherT.liftF(makeTaxEnrolmentCall(taxEnrolmentRequest))
      result <- EitherT.fromEither(handleTaxEnrolmentResponse(httpResponse)).leftFlatMap[Unit, Error] { error: Error =>
                 logger
                   .warn(
                     s"Failed to allocate enrolments due to error: $error; will store enrolment details"
                   )
                 taxEnrolmentRepository
                   .insert(taxEnrolmentRequest)
                   .leftMap(error => Error(s"Could not store enrolment details: $error"))
                   .subflatMap { writeResult =>
                     if (writeResult) Right(()) else Left(Error("Failed to store enrolment details"))
                   }
               }
    } yield result

  def handleTaxEnrolmentResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case 204   => Right(())
      case other => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def handleDatabaseResult(
    maybeTaxEnrolmentRequest: Option[TaxEnrolmentRequest]
  )(implicit hc: HeaderCarrier): Future[Boolean] =
    maybeTaxEnrolmentRequest match {
      case Some(enrolmentRequest) =>
        for {
          result <- EitherT.liftF(makeTaxEnrolmentCall(enrolmentRequest)) // attempt enrolment
          _      <- EitherT.fromEither(handleTaxEnrolmentResponse(result)) // evaluate enrolment result
          _      <- taxEnrolmentRepository.delete(enrolmentRequest.ggCredId) // delete record if enrolment was successful
        } yield ()
        Future.successful(true) // always return true regardless of whether the tax enrolment call/delete succeeded or not
      case None => Future.successful(false)
    }

  override def hasCgtSubscription(
    ggCredId: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Boolean] =
    for {
      maybeEnrolmentRequest <- taxEnrolmentRepository
                                .get(ggCredId)
                                .leftMap(
                                  error => Error(s"Could not check database to determine subscription status: $error")
                                )
      result <- EitherT.liftF(handleDatabaseResult(maybeEnrolmentRequest))
    } yield result

}
