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
  def hasCgtEnrolment(ggCredId: String)(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[TaxEnrolmentRequest]]
}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRepository: TaxEnrolmentRepository
) extends TaxEnrolmentService
    with Logging {

  def makeTaxEnrolmentCall(
    taxEnrolmentRequest: TaxEnrolmentRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] =
    taxEnrolmentConnector
      .allocateEnrolmentToGroup(taxEnrolmentRequest)
      .leftFlatMap[HttpResponse, Error](
        error => EitherT.fromEither[Future](Right(HttpResponse(999, Some(JsString(error.toString)))))
      )

  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] =
    for {
      httpResponse <- makeTaxEnrolmentCall(taxEnrolmentRequest)
      result <- EitherT.fromEither(handleTaxEnrolmentResponse(httpResponse)).leftFlatMap[Unit, Error] { error: Error =>
                 logger
                   .warn(
                     s"Failed to allocate enrolments due to error: $error; will inserting enrolment details in mongo."
                   )
                 taxEnrolmentRepository
                   .insert(taxEnrolmentRequest)
                   .leftMap(error => Error(s"Could not insert enrolment details into mongo: $error"))
                   .subflatMap { writeResult =>
                     if (writeResult) Right(()) else Left(Error("Failed to insert enrolment details into mongo"))
                   }
               }
    } yield result

  def handleTaxEnrolmentResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case 204   => Right(())
      case other => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

  override def hasCgtEnrolment(
    ggCredId: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    for {
      maybeEnrolmentRequest <- taxEnrolmentRepository
                                .get(ggCredId)
                                .leftMap(error => Error(s"Could not check existence of enrolment in mongo: $error"))
      taxEnrolmentRequest <- EitherT
                              .fromOption[Future](maybeEnrolmentRequest, Error("No enrolment request found in mongo"))
      httpResponse <- makeTaxEnrolmentCall(taxEnrolmentRequest)
      _ <- EitherT
            .fromEither[Future](handleTaxEnrolmentResponse(httpResponse))
            .leftMap(error => Error(s"Could not allocate enrolment: $error"))
      _ <- taxEnrolmentRepository
            .delete(ggCredId)
            .leftMap(error => Error(s"Could not delete enrolment request in mongo: $error"))
    } yield maybeEnrolmentRequest

}
