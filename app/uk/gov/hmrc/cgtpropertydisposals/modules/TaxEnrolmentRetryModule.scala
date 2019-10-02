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
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class TaxEnrolmentRetryModule extends AbstractModule {
  override def configure(): Unit =
    bind(classOf[TaxEnrolmentRetryProvider]).to(classOf[TaxEnrolmentRetryOrchestrator]).asEagerSingleton()
}

trait TaxEnrolmentRetryProvider {
  val taxEnrolmentRetryManager: ActorRef
}

@Singleton
class TaxEnrolmentRetryOrchestrator @Inject()(
  servicesConfig: ServicesConfig,
  taxEnrolmentConnector: TaxEnrolmentConnector,
  taxEnrolmentRetryRepository: TaxEnrolmentRetryRepository,
  system: ActorSystem
) extends TaxEnrolmentRetryProvider
    with Logging {

  val taxEnrolmentRetryManager: ActorRef = {
    logger.info("Starting Tax Enrolment Retry Manager")
    system.actorOf(
      TaxEnrolmentRetryManager.props(
        taxEnrolmentConnector,
        taxEnrolmentRetryRepository,
        system.scheduler
      )
    )
  }
}
