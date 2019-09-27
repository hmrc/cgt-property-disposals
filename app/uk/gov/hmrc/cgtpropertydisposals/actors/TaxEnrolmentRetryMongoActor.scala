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
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.RetryTaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryMongoActor.{DeleteTaxEnrolmentRetryRequest, GetAllNonFailedTaxEnrolmentRetryRequests, UpdateTaxEnrolmentRetryStatusToFail}
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps

class TaxEnrolmentRetryMongoActor(
  scheduler: Scheduler,
  taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
  maxStaggerTime: Int)
    extends Actor
    with Logging {

  import context.dispatcher
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def receive: Receive = {

    case GetAllNonFailedTaxEnrolmentRetryRequests =>
      val sender_ = sender()
      taxEnrolmentRetryRepository.getAllNonFailedEnrolmentRequests().value.map {
        case Left(error) => logger.warn(s"Error recovering tax enrolment retry requests: [$error]")
        case Right(enrolmentRequests) =>
          val rgen = new scala.util.Random
          enrolmentRequests.foreach { enrolmentRequest =>
            scheduler.scheduleOnce(
              FiniteDuration(1 + rgen.nextInt(maxStaggerTime - 1) + 1, TimeUnit.SECONDS), // randomly stagger messages
              sender_,
              RetryTaxEnrolmentRequest(initialiseTaxEnrolmentRequest(enrolmentRequest))
            )
          }
      }

    case request: UpdateTaxEnrolmentRetryStatusToFail =>
      taxEnrolmentRetryRepository.updateStatusToFail(request.taxEnrolmentRequest.userId).value.map {
        case Left(error) => logger.warn(s"Error updating status of retry request to fail : [$error]")
        case Right(maybeTaxEnrolmentRequest) =>
          maybeTaxEnrolmentRequest match {
            case Some(taxEnrolmentRequest) =>
              logger.info(s"Successfully updated status of retry request to fail: $taxEnrolmentRequest")
            case None => logger.warn(s"Could not find retry request with these details: ${request.taxEnrolmentRequest}")
          }
      }

    case request: DeleteTaxEnrolmentRetryRequest =>
      taxEnrolmentRetryRepository.delete(request.taxEnrolmentRequest.userId).value.map {
        case Left(error) => logger.warn(s"Error deleting tax enrolment retry request : [$error]")
        case Right(count) =>
          count match {
            case 1 => logger.info("Successfully deleted tax enrolment retry request")
            case _ =>
              logger.warn(s"Could not find retry request to delete with these details: ${request.taxEnrolmentRequest}")
          }
      }
  }
  def initialiseTaxEnrolmentRequest(taxEnrolmentRequest: TaxEnrolmentRequest): TaxEnrolmentRequest =
    taxEnrolmentRequest.copy(numOfRetries = 0, timeToNextRetryInSeconds = 0, currentElapsedTimeInSeconds = 0)
}

object TaxEnrolmentRetryMongoActor {
  final case object GetAllNonFailedTaxEnrolmentRetryRequests
  final case class UpdateTaxEnrolmentRetryStatusToFail(taxEnrolmentRequest: TaxEnrolmentRequest)
  final case class DeleteTaxEnrolmentRetryRequest(taxEnrolmentRequest: TaxEnrolmentRequest)

  def props(
    scheduler: Scheduler,
    taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
    maxStaggerTime: Int): Props =
    Props(new TaxEnrolmentRetryMongoActor(scheduler, taxEnrolmentRetryRepository, maxStaggerTime))
}
