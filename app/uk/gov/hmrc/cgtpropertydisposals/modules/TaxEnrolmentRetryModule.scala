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

package uk.gov.hmrc.cgtpropertydisposals.modules
import akka.actor.{ActorRef, ActorSystem}
import com.google.inject.{AbstractModule, Inject, Singleton}
import com.typesafe.config.{Config, ConfigFactory}
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import configs.syntax._
import play.api.Configuration

import scala.concurrent.duration.FiniteDuration

class TaxEnrolmentRetryModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[TaxEnrolmentRetryProvider]).to(classOf[TaxEnrolmentRetryOrchestrator]).asEagerSingleton()
}

trait TaxEnrolmentRetryProvider {
  val taxEnrolmentRetryManager: ActorRef
}

@Singleton
class TaxEnrolmentRetryOrchestrator @Inject()(
  config: Configuration,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
  system: ActorSystem
) extends TaxEnrolmentRetryProvider
    with Logging {

  val minBackoff: FiniteDuration = config.underlying.get[FiniteDuration]("tax-enrolment-retry.min-backoff").value
  val maxBackoff: FiniteDuration = config.underlying.get[FiniteDuration]("tax-enrolment-retry.max-backoff").value
  val numberOfRetriesBeforeDoublingInitialWait: Int =
    config.underlying.get[Int]("tax-enrolment-retry.number-of-retries-until-initial-wait-doubles").value
  val maxAllowableElapsedTime: FiniteDuration =
    config.underlying.get[FiniteDuration]("tax-enrolment-retry.max-elapsed-time").value
  val maxWaitTimeForRetryingRecoveredEnrolmentRequests: Int =
    config.underlying.get[Int]("tax-enrolment-retry.max-time-to-wait-after-recovery").value

  val taxEnrolmentRetryManager: ActorRef = {
    logger.info("Starting Tax Enrolment Retry Manager")
    system.actorOf(
      TaxEnrolmentRetryManager.props(
        minBackoff,
        maxBackoff,
        numberOfRetriesBeforeDoublingInitialWait,
        maxAllowableElapsedTime,
        maxWaitTimeForRetryingRecoveredEnrolmentRequests,
        taxEnrolmentConnector,
        taxEnrolmentRetryRepository,
        system.scheduler
      )
    )
  }
}
