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

package uk.gov.hmrc.cgtpropertydisposals.config

import com.google.inject.AbstractModule
import play.api.{Configuration, Environment}
import uk.gov.hmrc.cgtpropertydisposals.service.dms.{DmsSubmissionPoller, DmsSubmissionPollerImpl}

import java.time.Clock

class ApplicationModule(environment: Environment, config: Configuration) extends AbstractModule {
  override def configure(): Unit = {
    if (config.get[Boolean]("create-internal-auth-token-on-start")) {
      bind(classOf[InternalAuthTokenInitialiser]).to(classOf[InternalAuthTokenInitialiserImpl]).asEagerSingleton()
    } else {
      bind(classOf[InternalAuthTokenInitialiser]).to(classOf[NoOpInternalAuthTokenInitialiser]).asEagerSingleton()
    }
    bind(classOf[DmsSubmissionPoller]).to(classOf[DmsSubmissionPollerImpl]).asEagerSingleton()
    bind(classOf[Clock]).toInstance(Clock.systemDefaultZone())
  }
}