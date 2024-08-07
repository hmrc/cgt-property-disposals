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

package uk.gov.hmrc.cgtpropertydisposals.metrics

import com.codahale.metrics.{Counter, Timer}
import com.google.inject.{Inject, Singleton}

@Singleton
class Metrics @Inject() (metrics: com.codahale.metrics.MetricRegistry) {
  protected def timer(name: String): Timer = metrics.timer(s"backend.$name")

  protected def counter(name: String): Counter = metrics.counter(s"backend.$name")

  val registerWithIdTimer: Timer = timer("register-with-id.time")

  val registerWithIdErrorCounter: Counter = counter("register-with-id.errors.count")

  val registerWithoutIdTimer: Timer = timer("register-without-id.time")

  val subscriptionCreateTimer: Timer = timer("subscription-create.time")

  val subscriptionCreateErrorCounter: Counter = counter("subscription-create.errors.count")

  val subscriptionUpdateTimer: Timer = timer("subscription-update.time")

  val subscriptionUpdateErrorCounter: Counter = counter("subscription-update.errors.count")

  val subscriptionGetTimer: Timer = timer("subscription-get.time")

  val subscriptionGetErrorCounter: Counter = counter("subscription-get.errors.count")

  val subscriptionStatusTimer: Timer = timer("subscription-status.time")

  val subscriptionStatusErrorCounter: Counter = counter("subscription-status.errors.count")

  val eacdCreateEnrolmentTimer: Timer = timer("eacd.create-enrolment.time")

  val eacdCreateEnrolmentErrorCounter: Counter = counter("eacd.create-enrolment.errors.count")

  val eacdUpdateEnrolmentTimer: Timer = timer("eacd.update-enrolment.time")

  val eacdUpdateEnrolmentErrorCounter: Counter = counter("eacd.update-enrolment.errors.count")

  val subscriptionConfirmationEmailTimer: Timer = timer("email.subscription-confirmation.time")

  val subscriptionConfirmationEmailErrorCounter: Counter = counter("email.subscription-confirmation.errors.count")

  val submitReturnTimer: Timer = timer("submit-return.time")

  val submitReturnErrorCounter: Counter = counter("submit-return.errors.count")

  val submitReturnConfirmationEmailTimer: Timer = timer("email.submit-return-confirmation.time")

  val submitReturnConfirmationEmailErrorCounter: Counter = counter("email.submit-return-confirmation.errors.count")

  val financialDataTimer: Timer = timer("financial-data.time")

  val financialDataErrorCounter: Counter = counter("financial-data.errors.count")

  val listReturnsTimer: Timer = timer("list-returns.time")

  val listReturnsErrorCounter: Counter = counter("list-returns.errors.count")

  val displayReturnTimer: Timer = timer("display-return.time")

  val displayReturnErrorCounter: Counter = counter("display-return.errors.count")
}
