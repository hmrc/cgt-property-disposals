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
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.JsString
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.subscription.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.{TaxEnrolmentRepository, VerifiersRepository}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[TaxEnrolmentServiceImpl])
trait TaxEnrolmentService {
  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit]

  def hasCgtSubscription(ggCredId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Option[TaxEnrolmentRequest]]

  def updateVerifiers(updateVerifierDetails: UpdateVerifiersRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit]

}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRepository: TaxEnrolmentRepository,
  verifiersRepository: VerifiersRepository
) extends TaxEnrolmentService
    with Logging {

  def makeES8call(
    taxEnrolmentRequest: TaxEnrolmentRequest
  )(implicit hc: HeaderCarrier): Future[HttpResponse] =
    taxEnrolmentConnector
      .allocateEnrolmentToGroup(taxEnrolmentRequest)
      .value
      .map {
        case Left(error)         => HttpResponse(999, Some(JsString(error.toString)))
        case Right(httpResponse) => httpResponse
      }

  def makeES6call(
    updateVerifiersRequest: UpdateVerifiersRequest
  )(implicit hc: HeaderCarrier): Future[HttpResponse] =
    taxEnrolmentConnector
      .updateVerifiers(updateVerifiersRequest)
      .value
      .map {
        case Left(error)         => HttpResponse(999, Some(JsString(error.toString)))
        case Right(httpResponse) => httpResponse
      }

  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] =
    for {
      httpResponse <- EitherT.liftF(makeES8call(taxEnrolmentRequest))
      result <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse)).leftFlatMap[Unit, Error] {
                 error: Error =>
                   logger
                     .warn(
                       s"Failed to allocate enrolments due to error: $error; will store enrolment details"
                     )
                   taxEnrolmentRepository
                     .insert(taxEnrolmentRequest)
                     .leftMap(error => Error(s"Could not store enrolment details: $error"))
               }
    } yield result

  def handleTaxEnrolmentServiceResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case NO_CONTENT => Right(())
      case other      => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

  def handleEnrolmentState(
    enrolmentState: (Option[TaxEnrolmentRequest], Option[UpdateVerifiersRequest])
  )(implicit hc: HeaderCarrier): Future[Unit] =
    enrolmentState match {

      case (Some(createEnrolmentRequest), None) =>
        val result = for {
          httpResponse <- EitherT.liftF(makeES8call(createEnrolmentRequest)) // attempt to create enrolment
          _            <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse)) // evaluate enrolment result
          _            <- taxEnrolmentRepository.delete(createEnrolmentRequest.ggCredId) // delete record if enrolment was successful
        } yield ()
        result.leftMap(error => logger.warn(s"Error when trying to create enrolments again: $error")).merge

      case (None, Some(updateVerifiersRequest)) =>
        val result = for {
          httpResponse <- EitherT.liftF(makeES6call(updateVerifiersRequest))
          _            <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse))
          _            <- verifiersRepository.delete(updateVerifiersRequest.ggCredId)
        } yield ()
        result.leftMap(error => logger.warn(s"Error when updating verifiers: $error")).merge

      case (Some(enrolmentRequest), Some(updateVerifiersRequest)) =>
        val updatedCreateEnrolmentRequest =
          enrolmentRequest.copy(address = updateVerifiersRequest.subscribedUpdateDetails.newDetails.address)

        val result = for {
          _            <- taxEnrolmentRepository.update(enrolmentRequest.ggCredId, updatedCreateEnrolmentRequest)
          httpResponse <- EitherT.liftF(makeES8call(updatedCreateEnrolmentRequest)) // attempt to create enrolment
          _            <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse)) // evaluate enrolment result
          _            <- taxEnrolmentRepository.delete(enrolmentRequest.ggCredId) // delete record if enrolment was successful
          _            <- verifiersRepository.delete(enrolmentRequest.ggCredId) // delete the update verifier request
        } yield ()
        result.leftMap(error => logger.warn(s"Error when creating enrolments with updated verifiers: $error")).merge

      case (None, None) => Future.successful(())
    }

  override def hasCgtSubscription(
    ggCredId: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    for {
      maybeEnrolmentRequest <- taxEnrolmentRepository
                                .get(ggCredId)
                                .leftMap(
                                  error => Error(s"Could not check database to determine subscription status: $error")
                                )
      maybeUpdateVerifierRequest <- verifiersRepository
                                     .get(ggCredId)
                                     .leftMap(
                                       error =>
                                         Error(
                                           s"Could not check database to determine update verifier request exists : $error"
                                       )
                                     )
      _ <- EitherT.liftF(handleEnrolmentState(maybeEnrolmentRequest -> maybeUpdateVerifierRequest))
    } yield maybeEnrolmentRequest

  override def updateVerifiers(updateVerifiersRequest: UpdateVerifiersRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] =
    if (hasChangedAddress(updateVerifiersRequest.subscribedUpdateDetails)) {
      for {
        _                           <- verifiersRepository.delete(updateVerifiersRequest.ggCredId)
        _                           <- verifiersRepository.insert(updateVerifiersRequest)
        maybeCreateEnrolmentRequest <- taxEnrolmentRepository.get(updateVerifiersRequest.ggCredId)
        _                           <- EitherT.liftF(handleEnrolmentState(maybeCreateEnrolmentRequest -> Some(updateVerifiersRequest)))
      } yield ()
    } else {
      EitherT.rightT[Future, Error](())
    }

  private def hasChangedAddress(subscribedUpdateDetails: SubscribedUpdateDetails): Boolean =
    subscribedUpdateDetails.newDetails.address match {
      case Address.UkAddress(line1, line2, town, county, newPostcode) =>
        subscribedUpdateDetails.previousDetails.address match {
          case Address.UkAddress(line1, line2, town, county, oldPostcode) =>
            if (newPostcode === oldPostcode) false else true
          case Address.NonUkAddress(line1, line2, line3, line4, postcode, country) => true
        }
      case Address.NonUkAddress(line1, line2, line3, line4, postcode, newCountry) =>
        subscribedUpdateDetails.previousDetails.address match {
          case Address.UkAddress(line1, line2, town, county, postcode) => true
          case Address.NonUkAddress(line1, line2, line3, line4, postcode, previousCountry) =>
            if (newCountry === previousCountry) false else true
        }
    }

}
