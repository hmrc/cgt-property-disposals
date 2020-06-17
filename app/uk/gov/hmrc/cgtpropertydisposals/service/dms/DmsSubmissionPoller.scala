/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service.dms

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import cats.implicits._
import javax.inject.{Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.service.DmsSubmissionService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.workitem.{Failed, PermanentlyFailed, Succeeded}

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

@Singleton
class DmsSubmissionPoller @Inject() (
  actorSystem: ActorSystem,
  dmsSubmissionService: DmsSubmissionService,
  dmsSubmissionPollerContext: DmsSubmissionPollerContext,
  servicesConfig: ServicesConfig
)(implicit
  executionContext: ExecutionContext
) extends Logging {

  private val initialPollerDelay: FiniteDuration =
    FiniteDuration(servicesConfig.getDuration("dms.submission-poller.initial-delay").toNanos, TimeUnit.NANOSECONDS)

  private val pollerInterval: FiniteDuration =
    FiniteDuration(servicesConfig.getDuration("dms.submission-poller.interval").toNanos, TimeUnit.NANOSECONDS)

  private val failureCountLimit: Int = servicesConfig.getInt("dms.submission-poller.failure-count-limit")

  val _ = actorSystem.scheduler.schedule(initialPollerDelay, pollerInterval)(poller())(dmsSubmissionPollerContext)

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  def poller(): Unit = {
    val _ = dmsSubmissionService.dequeue.map {
      case Some(workItem) =>
        if (workItem.failureCount === failureCountLimit) {
          val _ = dmsSubmissionService.setResultStatus(workItem.id, PermanentlyFailed)
          ()
        } else {
          logger.info(
            s"DMS Submission poller: " +
              s"processing work item ${workItem.toString}"
          )

          implicit val hc: HeaderCarrier = workItem.item.headerCarrier

          dmsSubmissionService
            .submitToDms(
              workItem.item.html,
              workItem.item.formBundleId,
              workItem.item.cgtReference,
              workItem.item.completeReturn
            )
            .value
            .map {
              case Left(error)       =>
                logger.warn(
                  s"DMS Submission poller: " +
                    s"submission failed for work item ${workItem.toString} " +
                    s"with error - re-schedule for retry: ${error.toString}"
                )
                val _ = dmsSubmissionService.setProcessingStatus(workItem.id, Failed)
              case Right(envelopeId) =>
                logger.info(
                  s"DMS Submission poller: " +
                    s"submission succeeded for work item ${workItem.toString} " +
                    s"with envelope id : ${envelopeId.toString}"
                )
                val _ = dmsSubmissionService.setResultStatus(workItem.id, Succeeded)
            }
        }
      case None           =>
        logger.info("DMS Submission poller: no work items")
    }
  }

}
