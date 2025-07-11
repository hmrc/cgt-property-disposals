/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service.enrolments

import cats.data.EitherT
import cats.instances.future.*
import cats.syntax.eq.*
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.JsString
import uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments.{TaxEnrolmentRepository, VerifiersRepository}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxEnrolmentServiceImpl])
trait TaxEnrolmentService {

  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Unit]

  def hasCgtSubscription(ggCredId: String)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[TaxEnrolmentRequest]]

  def updateVerifiers(updateVerifierDetails: UpdateVerifiersRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Unit]

}

@Singleton
class TaxEnrolmentServiceImpl @Inject() (
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRepository: TaxEnrolmentRepository,
  verifiersRepository: VerifiersRepository,
  metrics: Metrics
)(implicit ec: ExecutionContext)
    extends TaxEnrolmentService
    with Logging {

  private def makeES8call(
    taxEnrolmentRequest: TaxEnrolmentRequest
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timer = metrics.eacdCreateEnrolmentTimer.time()

    taxEnrolmentConnector
      .allocateEnrolmentToGroup(taxEnrolmentRequest)
      .value
      .map { result =>
        timer.close()

        result match {
          case Left(error) =>
            metrics.eacdCreateEnrolmentErrorCounter.inc()
            HttpResponse(999, JsString(error.toString), Map.empty[String, Seq[String]])

          case Right(httpResponse) => httpResponse
        }
      }
  }

  private def makeES6call(
    updateVerifiersRequest: UpdateVerifiersRequest
  )(implicit hc: HeaderCarrier): Future[HttpResponse] = {
    val timer = metrics.eacdUpdateEnrolmentTimer.time()

    taxEnrolmentConnector
      .updateVerifiers(updateVerifiersRequest)
      .value
      .map { result =>
        timer.close()

        result match {
          case Left(error) =>
            metrics.eacdUpdateEnrolmentErrorCounter.inc()
            HttpResponse(999, JsString(error.toString), Map.empty[String, Seq[String]])

          case Right(httpResponse) => httpResponse
        }
      }
  }

  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] =
    for
      httpResponse <- EitherT.liftF(makeES8call(taxEnrolmentRequest))
      result       <-
        EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse)).leftFlatMap[Unit, Error] { error =>
          logger.warn(s"Failed to allocate enrolments due to error: $error; will store enrolment details")
          taxEnrolmentRepository
            .save(taxEnrolmentRequest)
            .leftMap(error => Error(s"Could not store enrolment details: $error"))
        }
    yield result

  private def handleTaxEnrolmentServiceResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case NO_CONTENT => Right(())
      case other      => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

  private def handleEnrolmentState(
    enrolmentState: (Option[TaxEnrolmentRequest], Option[UpdateVerifiersRequest])
  )(implicit hc: HeaderCarrier): Future[Unit] =
    enrolmentState match {

      case (Some(createEnrolmentRequest), None) =>
        val result = for
          httpResponse <- EitherT.liftF(makeES8call(createEnrolmentRequest)) // attempt to create enrolment
          _            <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse)) // evaluate enrolment result
          _            <- taxEnrolmentRepository.delete(
                            createEnrolmentRequest.ggCredId
                          ) // delete record if enrolment was successful
        yield ()
        result
          .bimap(
            error => logger.warn(s"Error when trying to create enrolments again: $error"),
            _ => logger.info("Successfully enrolled user to cgt")
          )
          .merge

      case (None, Some(updateVerifiersRequest)) =>
        val result = for
          httpResponse <- EitherT.liftF(makeES6call(updateVerifiersRequest))
          _            <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse))
          _            <- verifiersRepository.delete(updateVerifiersRequest.ggCredId)
        yield ()
        result
          .bimap(
            error => logger.warn(s"Error when updating verifiers: $error"),
            _ => logger.info("Successfully updated verifiers in enrolment")
          )
          .merge

      case (Some(enrolmentRequest), Some(updateVerifiersRequest)) =>
        val updatedCreateEnrolmentRequest =
          enrolmentRequest.copy(address = updateVerifiersRequest.subscribedUpdateDetails.newDetails.address)

        val result = for
          _            <- taxEnrolmentRepository.update(enrolmentRequest.ggCredId, updatedCreateEnrolmentRequest)
          httpResponse <- EitherT.liftF(makeES8call(updatedCreateEnrolmentRequest)) // attempt to create enrolment
          _            <- EitherT.fromEither(handleTaxEnrolmentServiceResponse(httpResponse)) // evaluate enrolment result
          _            <- taxEnrolmentRepository.delete(enrolmentRequest.ggCredId) // delete record if enrolment was successful
          _            <- verifiersRepository.delete(enrolmentRequest.ggCredId) // delete the update verifier request
        yield ()
        result
          .bimap(
            error => logger.warn(s"Error when creating enrolments with updated verifiers: $error"),
            _ => logger.info("Successfully updated verifiers in enrolment after previous enrolment failure")
          )
          .merge

      case (None, None) => Future.successful(())
    }

  override def hasCgtSubscription(
    ggCredId: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    for
      maybeEnrolmentRequest      <-
        taxEnrolmentRepository
          .get(ggCredId)
          .leftMap(error => Error(s"Could not check database to determine subscription status: $error"))
      maybeUpdateVerifierRequest <-
        verifiersRepository
          .get(ggCredId)
          .leftMap(error =>
            Error(
              s"Could not check database to determine update verifier request exists : $error"
            )
          )
      _                          <- EitherT.liftF(handleEnrolmentState(maybeEnrolmentRequest -> maybeUpdateVerifierRequest))
    yield maybeEnrolmentRequest

  override def updateVerifiers(updateVerifiersRequest: UpdateVerifiersRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] =
    if hasChangedAddress(updateVerifiersRequest.subscribedUpdateDetails) then
      for
        _                           <- verifiersRepository.delete(updateVerifiersRequest.ggCredId)
        _                           <- verifiersRepository.insert(updateVerifiersRequest)
        maybeCreateEnrolmentRequest <- taxEnrolmentRepository.get(updateVerifiersRequest.ggCredId)
        _                           <- EitherT.liftF(handleEnrolmentState(maybeCreateEnrolmentRequest -> Some(updateVerifiersRequest)))
      yield ()
    else EitherT.rightT[Future, Error](())

  private def hasChangedAddress(subscribedUpdateDetails: SubscribedUpdateDetails): Boolean =
    subscribedUpdateDetails.newDetails.address match {
      case Address.UkAddress(_, _, _, _, newPostcode)      =>
        subscribedUpdateDetails.previousDetails.address match {
          case Address.UkAddress(_, _, _, _, oldPostcode) =>
            if newPostcode.equals(oldPostcode) then false else true
          case _: Address.NonUkAddress                    => true
        }
      case Address.NonUkAddress(_, _, _, _, _, newCountry) =>
        subscribedUpdateDetails.previousDetails.address match {
          case _: Address.UkAddress                                 => true
          case Address.NonUkAddress(_, _, _, _, _, previousCountry) =>
            if newCountry === previousCountry then false else true
        }
    }

}
