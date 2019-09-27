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

import akka.actor.{Actor, ActorRef, ActorRefFactory, Props, Scheduler, Terminated}
import com.typesafe.config.{Config, ConfigFactory}
import net.ceedubs.ficus.Ficus._
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryConnectorActor.RetryCallToTaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.{MarkTaxEnrolmentRetryRequestAsSuccess, RetryTaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryMongoActor.{DeleteTaxEnrolmentRetryRequest, GetAllNonFailedTaxEnrolmentRetryRequests, UpdateTaxEnrolmentRetryStatusToFail}
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration.{FiniteDuration, _}

class TaxEnrolmentRetryManager(
  createMongoActor: ActorRefFactory => ActorRef,
  createConnectorActor: ActorRefFactory => ActorRef,
  taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
  taxEnrolmentService: TaxEnrolmentService,
  scheduler: Scheduler
) extends Actor
    with Logging {
  import context.dispatcher
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val config: Config = ConfigFactory.load()

  val minBackoff: FiniteDuration = config.as[FiniteDuration]("tax-enrolment-retry.min-backoff")
  val maxBackoff: FiniteDuration = config.as[FiniteDuration]("tax-enrolment-retry.max-backoff")
  val numberOfRetriesUntilInitialWaitDoubles: Int =
    config.as[Int]("tax-enrolment-retry.number-of-retries-until-initial-wait-doubles")
  val maxAllowableElapsedTime: FiniteDuration = config.as[FiniteDuration]("tax-enrolment-retry.max-elapsed-time")

  override def preStart(): Unit = {
    super.preStart()

    val mongoActorRef     = createMongoActor(context)
    val connectorActorRef = createConnectorActor(context)

    context.watch(mongoActorRef)
    context.watch(connectorActorRef)

    mongoActorRef ! GetAllNonFailedTaxEnrolmentRetryRequests
    context.become(initialised(mongoActorRef, connectorActorRef))
  }

  def initialised(mongo: ActorRef, connector: ActorRef): Receive = {
    case Terminated(deadActor) => {
      if (deadActor == mongo) {
        logger.info("Creating new mongo actor")
        val newMongoActor = createMongoActor(context)
        context.watch(newMongoActor)
        context.become(initialised(newMongoActor, connector))
      } else if (deadActor == connector) {
        logger.info("Creating new connector actor")
        val newConnectorActor = createConnectorActor(context)
        context.watch(newConnectorActor)
        context.become(initialised(mongo, newConnectorActor))
      } else
        logger.warn(s"Unexpected terminated message - dead actor ref: ${deadActor.toString}")
    }

    case retry: RetryTaxEnrolmentRequest =>
      if (retry.taxEnrolmentRequest.currentElapsedTimeInSeconds >= maxAllowableElapsedTime.toSeconds) {
        logger.info("Elapsed time has exceed maximum allowed time allowed to retry - marking request as failed")
        mongo ! UpdateTaxEnrolmentRetryStatusToFail(retry.taxEnrolmentRequest)
      } else {
        val interval = nextScheduledTime(retry.taxEnrolmentRequest.numOfRetries)
        val retryRequest = RetryCallToTaxEnrolmentService(
          retry.taxEnrolmentRequest.copy(
            numOfRetries                = retry.taxEnrolmentRequest.numOfRetries + 1,
            timeToNextRetryInSeconds    = interval.toSeconds,
            currentElapsedTimeInSeconds = retry.taxEnrolmentRequest.currentElapsedTimeInSeconds + retry.taxEnrolmentRequest.timeToNextRetryInSeconds
          )
        )
        logger.info(s"Retrying request with following details: [${retryRequest.taxEnrolmentRequest}]")
        scheduler.scheduleOnce(interval, connector, retryRequest)
      }

    case request: MarkTaxEnrolmentRetryRequestAsSuccess =>
      mongo ! DeleteTaxEnrolmentRetryRequest(request.taxEnrolmentRequest)

  }

  override def receive: Receive = PartialFunction.empty

  def nextScheduledTime(numberOfRetries: Int): FiniteDuration = {
    val minMillis         = minBackoff.toMillis.toDouble
    val maxMillis         = maxBackoff.toMillis.toDouble
    val exponentialFactor = math.log((minMillis - maxMillis) / (2.0 * minMillis - maxMillis)) / numberOfRetriesUntilInitialWaitDoubles.toDouble
    val millis: Double    = (minMillis - maxMillis) * math.exp(-exponentialFactor * numberOfRetries.toDouble) + maxMillis
    millis.millis.min(maxBackoff)
  }
}

object TaxEnrolmentRetryManager {
  final case class RetryTaxEnrolmentRequest(taxEnrolmentRequest: TaxEnrolmentRequest)
  final case class MarkTaxEnrolmentRetryRequestAsSuccess(taxEnrolmentRequest: TaxEnrolmentRequest)
  def props(
    createMongoActor: ActorRefFactory => ActorRef,
    createConnectorActor: ActorRefFactory => ActorRef,
    taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
    taxEnrolmentService: TaxEnrolmentService,
    scheduler: Scheduler
  ): Props =
    Props(
      new TaxEnrolmentRetryManager(
        createMongoActor,
        createConnectorActor,
        taxEnrolmentRetryRepository,
        taxEnrolmentService,
        scheduler
      )
    )
}
