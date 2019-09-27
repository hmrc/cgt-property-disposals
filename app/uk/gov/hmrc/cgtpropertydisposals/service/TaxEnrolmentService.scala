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
import play.api.http.Status._
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxEnrolmentError, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
@ImplementedBy(classOf[TaxEnrolmentServiceImpl])
trait TaxEnrolmentService {
  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, TaxEnrolmentError, TaxEnrolmentResponse]
}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(
  connector: TaxEnrolmentConnector,
  taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository
) extends TaxEnrolmentService
    with Logging {

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, TaxEnrolmentError, TaxEnrolmentResponse] = {

    val result: EitherT[Future, Error, HttpResponse] = for {
      _        <- taxEnrolmentRetryRepository.insert(taxEnrolmentRequest)
      response <- connector.allocateEnrolmentToGroup(taxEnrolmentRequest)
    } yield response

    EitherT(
      result.fold[Either[TaxEnrolmentError, TaxEnrolmentResponse]](
        _ => {
          logger.warn(
            "Could not allocate enrolments to group due to unexpected exception received - will schedule to retry asynchronously"
          )
          Left(TaxEnrolmentError(taxEnrolmentRequest))
        },
        httpResponse =>
          httpResponse.status match {
            case NO_CONTENT => {
              logger.info("Successfully allocated enrolments to group")
              Right(TaxEnrolmentCreated)
            }
            case status => {
              logger.warn(
                s"Could not allocate enrolment to group due to unexpected error - http response status is: $status"
              )
              if (is5xx(httpResponse.status)) {
                Left(TaxEnrolmentError(taxEnrolmentRequest))
              } else {
                Right(TaxEnrolmentFailedForSomeOtherReason(taxEnrolmentRequest, httpResponse))
              }
            }
        }
      )
    )
  }

  private def is5xx(status: Int): Boolean = status >= 500 && status < 600
}

object TaxEnrolmentService {

  sealed trait TaxEnrolmentResponse extends Product with Serializable

  object TaxEnrolmentResponse {

    case object TaxEnrolmentCreated extends TaxEnrolmentResponse

    case object UnauthorisedTaxEnrolmentRequest extends TaxEnrolmentResponse

    case object BadTaxEnrolmentRequest extends TaxEnrolmentResponse

    final case class TaxEnrolmentFailedForSomeOtherReason(
      taxEnrolmentRequest: TaxEnrolmentRequest,
      httpResponse: HttpResponse
    ) extends TaxEnrolmentResponse

  }

}
