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

import java.time.LocalDateTime
import org.scalacheck.ScalacheckShapeless._
import akka.actor.{ActorRefFactory, ActorSystem, PoisonPill, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import cats.data.EitherT
import com.miguno.akka.testing.VirtualTime
import org.scalacheck.{Arbitrary, Gen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import uk.gov.hmrc.cgtpropertydisposals.actors.FakeChildMaker.NewChildCreated
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryConnectorActor.RetryCallToTaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.{MarkTaxEnrolmentRetryRequestAsSuccess, RetryTaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, Error, TaxEnrolmentRequest, sample}
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService

import scala.concurrent.Future

class TaxEnrolmentRetryManagerSpec
    extends TestKit(ActorSystem())
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockFactory {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  val time: VirtualTime = new VirtualTime()

  val mockTaxEnrolmentService    = mock[TaxEnrolmentService]
  val mockTaxEnrolmentRepository = mock[TaxEnrolmentRetryRepository]

  implicit val localDateArb: Arbitrary[LocalDateTime] = Arbitrary.apply[LocalDateTime](Gen.const(LocalDateTime.now()))
  val taxEnrolmentRequest                             = sample[TaxEnrolmentRequest]

  "A Tax Enrolment Retry Manager actor" must {

    "send a message to the mongo actor if tax enrolnment call was successful" in {
      val mongoProbe     = TestProbe()
      val connectorProbe = TestProbe()

      val app = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            (_: ActorRefFactory) => new FakeChildMaker(self, mongoProbe.ref).newChild(),
            (_: ActorRefFactory) => new FakeChildMaker(self, connectorProbe.ref).newChild(),
            mockTaxEnrolmentRepository,
            mockTaxEnrolmentService,
            system.scheduler
          )
        )
      )

      val taxEnrolmentRequest = TaxEnrolmentRequest(
        "userId",
        "someref",
        Address.UkAddress("line1", None, None, None, "KO"),
        "InProgress",
        2,
        0,
        249237542,
        LocalDateTime.now()
      )

      app ! MarkTaxEnrolmentRetryRequestAsSuccess(taxEnrolmentRequest)

      mongoProbe.receiveN(2)
    }

    "send a message to the mongo actor if the elapsed time has exceeded the max elapsed time" in {
      val mongoProbe     = TestProbe()
      val connectorProbe = TestProbe()

      val app = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            (_: ActorRefFactory) => new FakeChildMaker(self, mongoProbe.ref).newChild(),
            (_: ActorRefFactory) => new FakeChildMaker(self, connectorProbe.ref).newChild(),
            mockTaxEnrolmentRepository,
            mockTaxEnrolmentService,
            system.scheduler
          )
        )
      )

      val taxEnrolmentRequest = TaxEnrolmentRequest(
        "userId",
        "someref",
        Address.UkAddress("line1", None, None, None, "KO"),
        "InProgress",
        2,
        0,
        249237542,
        LocalDateTime.now()
      )

      app ! RetryTaxEnrolmentRequest(taxEnrolmentRequest)
      mongoProbe.receiveN(2)
    }

    "send a message to the connector actor if the elapsed time has exceeded the max elapsed time" in {
      val mongoProbe     = TestProbe()
      val connectorProbe = TestProbe()

      val app = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            (_: ActorRefFactory) => new FakeChildMaker(self, mongoProbe.ref).newChild(),
            (_: ActorRefFactory) => new FakeChildMaker(self, connectorProbe.ref).newChild(),
            mockTaxEnrolmentRepository,
            mockTaxEnrolmentService,
            system.scheduler
          )
        )
      )

      val taxEnrolmentRequest = TaxEnrolmentRequest(
        "userId",
        "someref",
        Address.UkAddress("line1", None, None, None, "KO"),
        "InProgress",
        0,
        0,
        0,
        LocalDateTime.of(2019, 12, 1, 12, 10)
      )

      app ! RetryTaxEnrolmentRequest(taxEnrolmentRequest)
      // need to advance the time
//      time.advance(FiniteDuration(10, TimeUnit.SECONDS))

      connectorProbe.expectMsg(
        RetryCallToTaxEnrolmentService(
          TaxEnrolmentRequest(
            "userId",
            "someref",
            UkAddress("line1", None, None, None, "KO"),
            "InProgress",
            1,
            1,
            0,
            LocalDateTime.of(2019, 12, 1, 12, 10)
          )
        )
      )
    }

    "must restart the mongo actor if it dies" in {
      val mongoProbe     = TestProbe()
      val connectorProbe = TestProbe()

      system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            (_: ActorRefFactory) => new FakeChildMaker(self, mongoProbe.ref).newChild(),
            (_: ActorRefFactory) => new FakeChildMaker(self, connectorProbe.ref).newChild(),
            mockTaxEnrolmentRepository,
            mockTaxEnrolmentService,
            system.scheduler
          )
        )
      )
      mongoProbe.ref ! PoisonPill
      expectMsg(NewChildCreated)
    }

    "must restart the connector actor if it dies" in {
      val mongoProbe     = TestProbe()
      val connectorProbe = TestProbe()

      system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            (_: ActorRefFactory) => new FakeChildMaker(self, mongoProbe.ref).newChild(),
            (_: ActorRefFactory) => new FakeChildMaker(self, connectorProbe.ref).newChild(),
            mockTaxEnrolmentRepository,
            mockTaxEnrolmentService,
            system.scheduler
          )
        )
      )
      connectorProbe.ref ! PoisonPill
      expectMsg(NewChildCreated)
    }

  }
}
