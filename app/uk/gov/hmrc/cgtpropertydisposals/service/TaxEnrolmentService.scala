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
//import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
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
}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRetryRepository: TaxEnrolmentRepository
) extends TaxEnrolmentService
    with Logging {

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] = {

    val taxEnrolmentResponse: EitherT[Future, Error, Unit] = for {
      httpResponse <- taxEnrolmentConnector
                       .allocateEnrolmentToGroup(taxEnrolmentRequest)
                       .leftMap(e => Error(s"Error calling tax enrolment service: $e"))
      result <- EitherT.fromEither(handleTaxEnrolmentResponse(httpResponse))
    } yield result

    EitherT.liftF(
      taxEnrolmentResponse.value.map {
        case Left(error) => {
          logger.warn(s"Failed to allocate enrolments due to error: $error. Inserting enrolment details in mongo.")
          val dbResponse = for {
            writeResult <- taxEnrolmentRetryRepository
                            .insert(taxEnrolmentRequest)
                            .leftMap(error => Error(s"Error inserting enrolment details into mongo: $error"))
          } yield (writeResult)

          dbResponse.fold[Either[Error, Unit]](
            error => Left(error),
            writeResult =>
              if (writeResult) {
                Right(())
              } else {
                Left(Error("Failed to insert enrolment details into mongo"))
            }
          )
        }
        case Right(enrolmentSuccess) => enrolmentSuccess
      }
    )
  }

  def handleTaxEnrolmentResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case 204   => Right(())
      case other => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

}
