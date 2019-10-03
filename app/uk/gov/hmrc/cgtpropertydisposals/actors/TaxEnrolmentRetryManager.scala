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

package uk.gov.hmrc.cgtpropertydisposals.actors

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props, Scheduler}
import akka.pattern.pipe
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager._
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future
import scala.concurrent.duration._

class TaxEnrolmentRetryManager(
  minBackoff: FiniteDuration,
  maxBackoff: FiniteDuration,
  numberOfRetriesBeforeDoublingInitialWait: Int,
  maxAllowableElapsedTime: FiniteDuration,
  maxWaitTimeForRetryingRecoveredEnrolmentRequests: Int,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
  scheduler: Scheduler
) extends Actor
    with Logging {

  import context.dispatcher
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def preStart(): Unit = {
    super.preStart()
    taxEnrolmentRetryRepository.getAllNonFailedEnrolmentRequests().value.map {
      case Left(error) => logger.warn(s"Could not recover tax enrolment retry requests: $error")
      case Right(enrolmentRequests) =>
        val rgen = new scala.util.Random
        enrolmentRequests.foreach { enrolmentRequest =>
          scheduler.scheduleOnce(
            FiniteDuration(1 + rgen.nextInt(maxWaitTimeForRetryingRecoveredEnrolmentRequests - 1), TimeUnit.SECONDS), // randomly stagger messages
            self,
            RetryTaxEnrolmentRequest(enrolmentRequest, 0, 0, 0)
          )
        }
    }
  }

  override def receive: Receive = {
    case request: QueueTaxEnrolmentRequest =>
      taxEnrolmentRetryRepository.insert(request.taxEnrolmentRequest).value.map {
        case Left(error) => {
          logger.warn(s"Failed to queue tax enrolment request: $error")
          Future.successful(QueueTaxEnrolmentRequestFailure) pipeTo sender()
        }
        case Right(writeResult) => {
          if (writeResult) {
            logger.info(s"Successfully queued tax enrolment request")
            Future.successful(QueueTaxEnrolmentRequestSuccess) pipeTo sender()
          } else {
            logger.warn(s"Did not queue tax enrolment record with write result: $writeResult")
            Future.successful(QueueTaxEnrolmentRequestFailure) pipeTo sender()
          }
        }
      }
      self ! CallTaxEnrolmentService(request.taxEnrolmentRequest)

    case request: CallTaxEnrolmentService =>
      taxEnrolmentConnector.allocateEnrolmentToGroup(request.taxEnrolmentRequest).value.map {
        case Left(error) => {
          logger.warn(s"Error calling tax enrolment service: $error - retrying...")
          self ! RetryTaxEnrolmentRequest(request.taxEnrolmentRequest, 0, 0, 0)
        }
        case Right(httpResponse) => {
          httpResponse.status match {
            case 204 => {
              taxEnrolmentRetryRepository.delete(request.taxEnrolmentRequest.ggCredId).value.map {
                case Left(error) => {
                  logger.error(s"Could not delete tax enrolment record: $error")
                }
                case Right(_) => {
                  logger.info("Successfully deleted tax enrolment record")
                }
              }
            }
            case other => {
              if (is5xx(httpResponse.status)) {
                self ! RetryTaxEnrolmentRequest(request.taxEnrolmentRequest, 0, 0, 0)
              } else {
                logger.warn(s"Could not allocate tax enrolments. Received $other http status")
              }
            }
          }
        }
      }

    case retry: RetryTaxEnrolmentRequest => {
      if (retry.currentElapsedTimeInSeconds >= maxAllowableElapsedTime.toSeconds) {
        taxEnrolmentRetryRepository.updateStatusToFail(retry.taxEnrolmentRequest.ggCredId).value.map {
          case Left(error) =>
            logger.error(s"Could not update status of tax enrolment request to fail $error")
          case Right(_) =>
            logger.info(s"Maximum elapsed time has been exceed. Marked request as failed. Request details $retry")
        }
      } else {
        val interval = nextScheduledTime(retry.numOfRetries)
        val retryRequest = retry.copy(
          numOfRetries                = retry.numOfRetries + 1,
          timeToNextRetryInSeconds    = interval.toSeconds,
          currentElapsedTimeInSeconds = retry.currentElapsedTimeInSeconds + retry.timeToNextRetryInSeconds
        )

        logger.info(
          s"Retrying request [request-id: ${retry.taxEnrolmentRequest.requestId}] " +
            s"in ${interval.toSeconds} seconds: current total elapsed time is ${retryRequest.currentElapsedTimeInSeconds} " +
            s"with max allowable elapsed time set to $maxAllowableElapsedTime"
        )

        scheduler.scheduleOnce(
          interval,
          self,
          retryRequest
        )
      }
    }
  }

  def nextScheduledTime(numberOfRetries: Int): FiniteDuration = {
    val minMillis         = minBackoff.toMillis.toDouble
    val maxMillis         = maxBackoff.toMillis.toDouble
    val exponentialFactor = math.log((minMillis - maxMillis) / (2.0 * minMillis - maxMillis)) / numberOfRetriesBeforeDoublingInitialWait.toDouble
    val millis: Double    = (minMillis - maxMillis) * math.exp(-exponentialFactor * numberOfRetries.toDouble) + maxMillis
    millis.millis.min(maxBackoff)
  }

  def is5xx(status: Int): Boolean = status >= 500 && status < 600

}

object TaxEnrolmentRetryManager {

  final case class RetryTaxEnrolmentRequest(
    taxEnrolmentRequest: TaxEnrolmentRequest,
    numOfRetries: Int,
    timeToNextRetryInSeconds: Long,
    currentElapsedTimeInSeconds: Long
  )

  final case class QueueTaxEnrolmentRequest(taxEnrolmentRequest: TaxEnrolmentRequest)

  final case class CallTaxEnrolmentService(taxEnrolmentRequest: TaxEnrolmentRequest)

  sealed trait QueueTaxEnrolmentResponse
  final case object QueueTaxEnrolmentRequestFailure extends QueueTaxEnrolmentResponse
  final case object QueueTaxEnrolmentRequestSuccess extends QueueTaxEnrolmentResponse

  def props(
    minBackoff: FiniteDuration,
    maxBackoff: FiniteDuration,
    numberOfRetriesBeforeDoublingInitialWait: Int,
    maxAllowableElapsedTime: FiniteDuration,
    maxWaitTimeForRetryingRecoveredEnrolmentRequests: Int,
    taxEnrolmentConnector: TaxEnrolmentConnector,
    taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
    scheduler: Scheduler
  ): Props =
    Props(
      new TaxEnrolmentRetryManager(
        minBackoff,
        maxBackoff,
        numberOfRetriesBeforeDoublingInitialWait,
        maxAllowableElapsedTime,
        maxWaitTimeForRetryingRecoveredEnrolmentRequests,
        taxEnrolmentConnector,
        taxEnrolmentRetryRepository,
        scheduler
      )
    )
}
