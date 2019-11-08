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
import play.api.http.Status.NO_CONTENT
import play.api.libs.json.JsString
import uk.gov.hmrc.auth.core.retrieve.GGCredId
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.{KeyValuePair, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
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

  def updateVerifiers(ggCredId: String, cgtReference: CgtReference, previous: Address, current: Address)(
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

  def makeUpdateVerifierCall(
    cgtReference: CgtReference,
    previousVerifier: KeyValuePair,
    newVerifier: KeyValuePair
  )(implicit hc: HeaderCarrier): Future[HttpResponse] =
    taxEnrolmentConnector
      .updateVerifiers(cgtReference, previousVerifier, newVerifier)
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
               }
    } yield result

  def handleTaxEnrolmentResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case NO_CONTENT => Right(())
      case other      => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

  def handleUpdateVerifierResponse(httpResponse: HttpResponse): Either[Error, Unit] =
    httpResponse.status match {
      case NO_CONTENT => Right(())
      case other      => Left(Error(s"Received error response from tax enrolment service with http status: $other"))
    }

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def handleDatabaseResult(
    maybeTaxEnrolmentRequest: Option[TaxEnrolmentRequest]
  )(implicit hc: HeaderCarrier): Future[Unit] =
    maybeTaxEnrolmentRequest match {
      case Some(enrolmentRequest) =>
        val result = for {
          result <- EitherT.liftF(makeTaxEnrolmentCall(enrolmentRequest)) // attempt enrolment
          _      <- EitherT.fromEither(handleTaxEnrolmentResponse(result)) // evaluate enrolment result
          _      <- taxEnrolmentRepository.delete(enrolmentRequest.ggCredId) // delete record if enrolment was successful
        } yield ()
        result.leftMap(error => logger.warn(s"Error when retrying allocation of enrolments: $error")).merge
      case None => Future.successful(())
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
      _ <- EitherT.liftF(handleDatabaseResult(maybeEnrolmentRequest))
    } yield maybeEnrolmentRequest

  override def updateVerifiers(ggCredId: String, cgtReference: CgtReference, )(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] = {

    val result = for {
      // first insert the update verifiers into the database in case the call fails
      _ <- verifiersRepository.insert(updateVerifiersRequest)
      // go to the database and check if the record exists
      maybeEnrolmentRequest <- taxEnrolmentRepository.get(ggCredId)
      // if this call is successful then the we need to make another call

      _ <- handleTheUpdateVerifiersResult(ggCredId, maybeEnrolmentRequest, updateVerifiersRequest)
      // if this returns a None then we call the ES6 API but if it returns a row then we need to update that row first
      //      _ <- EitherT.liftF(handleTheUpdateVerifiersResult(maybeEnrolmentRequest, current)]
      //      // if the row exists then we want to update the information in that row and make the ES8 call - if the row does not exist then we make the ES6 call
      //      httpResponse <- EitherT.liftF(makeTaxEnrolmentCall(maybeEnrolmentRequest)) // - this is the ES8 call

    } yield ()

    result.fold(
      error =>
      success =>
    )
  }

  private def handleTheUpdateVerifiersResult(
    ggCredId: String,
    cgtReference: CgtReference,
    maybeEnrolmentRequest: Option[TaxEnrolmentRequest],
    previous : Address,
    current : Address
  )(implicit hc: HeaderCarrier): Unit =
    maybeEnrolmentRequest match {
      case Some(enrolmentRequest) => {
        // update the row in the database
        for {
          _ <- taxEnrolmentRepository
                .update(ggCredId, enrolmentRequest.copy(address = current))
                .leftMap(
                  error =>
                    Error(
                      s"Could not update the retry enrolment record with the updated verifier information: $error"
                    )
                )
        _ <-

        } yield (maybeTaxEnrolmentRepository)

        // we make the ES8 call
        makeTaxEnrolmentCall(enrolmentRequest)
      }
      case None => {
        // This means there is no row in the database
        // Therefore we need to just call the ES6 endpoint and go - if that fails then we need to store it to replay it back when they log in again
        taxEnrolmentConnector.updateVerifiers(cgtReference, Address.toVerifier(previous), Address.toVerifier(current)).value.map {
          case Left(a) => // if this fails then we want to store it in a database
          case Right(b) => // if this succeeds then we delete it from the database //TODO: insert it somewhere first
        }
        for {
        _ <- taxEnrolmentConnector.updateVerifiers(cgtReference, Address.toVerifier(previous), Address.toVerifier(current))
        _ <- handleThisUpdateVErifiersCall() //if sccuess then reutrn success so that the beloew delete happens else return error in which delete does not happen (then we need to make a call somewhere on their return)
        _ <- verifiersRepository.delete(GGCredId(ggCredId))
        }yield()
      }
    }

}
