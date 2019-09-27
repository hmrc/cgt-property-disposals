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

  def mockInsertTaxEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Boolean]
  ) =
    (mockTaxEnrolmentRepository
      .insert(_: TaxEnrolmentRequest))
      .expects(taxEnrolmentRequest)
      .returning(EitherT(Future.successful(response)))

  def mockDeleteTaxEnrolment(userId: String)(
    response: Either[Error, Int]
  ) =
    (mockTaxEnrolmentRepository
      .delete(_: String))
      .expects(userId)
      .returning(EitherT(Future.successful(response)))

  def mockUpdateStatusToFail(userId: String)(
    response: Either[Error, Option[TaxEnrolmentRequest]]
  ) =
    (mockTaxEnrolmentRepository
      .updateStatusToFail(_: String))
      .expects(userId)
      .returning(EitherT(Future.successful(response)))

  def mockGetAllNonFailedEnrolmentRequests()(
    response: Either[Error, List[TaxEnrolmentRequest]]
  ) =
    (mockTaxEnrolmentRepository.getAllNonFailedEnrolmentRequests _)
      .expects()
      .returning(EitherT(Future.successful(response)))

//  val actor =
//    system.actorOf(
//      TaxEnrolmentRetryManager
//        .props(mockTaxEnrolmentRepository, mockTaxEnrolmentService, mongoListener.ref, connectorListener.ref, scheduler)
//    )

  implicit val localDateArb: Arbitrary[LocalDateTime] = Arbitrary.apply[LocalDateTime](Gen.const(LocalDateTime.now()))
  val taxEnrolmentRequest                             = sample[TaxEnrolmentRequest]
  // val taxEnrolmentRequest                             = sample[TaxEnrolmentRequest]
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

      //mongoProbe.expectMsg(UpdateTaxEnrolmentRetryStatusToFail(taxEnrolmentRequest))
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

      //mongoProbe.expectMsg(UpdateTaxEnrolmentRetryStatusToFail(taxEnrolmentRequest))
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

//    "send a message to the mongo actor if the tax enrolment request was successful" in {
//      val taxEnrolmentRequest = TaxEnrolmentRequest(
//        "userId",
//        "someref",
//        Address.UkAddress("line1", None, None, None, "KO"),
//        "InProgress",
//        2,
//        0,
//        1,
//        LocalDateTime.now()
//      )
//      actor ! MarkTaxEnrolmentRetryRequestAsSuccess(taxEnrolmentRequest)
//      mongoListener.expectMsg(DeleteTaxEnrolmentRetryRequest(taxEnrolmentRequest))
//    }

    "must restart the mongo actor if it dies" in {
      // mockGetAllNonFailedEnrolmentRequests()(Right(List(taxEnrolmentRequest)))

      // here you will need to create a new instance of this class and then pass in for the function
      // the child actor provider class that will send a message to the
      // the test probe then will look for this message and it if finds when the poison pill is sent then it means
      // that it has created the a0ctor
      //mongoListener.ref ! PoisonPill // I need to then create a mongo actor and then get a reference to it, once I get a reference to it
      // then I pass that to the above retry manager - once I get that, then I send a poison pill to that mongo actor
      // then the actor system will receive a terminated message
      // once it receives a terminated message it will then instantiate the new mongo actor via the child provider class which then
      // send a message saying that it has created an actor
      // then some actor, maybe, the test-actor can do the expectMsg

      //val childMaker: ChildMaker = new ChildMaker(self)
      //val child                  = childMaker.newChild()

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
      // mockGetAllNonFailedEnrolmentRequests()(Right(List(taxEnrolmentRequest)))

      // here you will need to create a new instance of this class and then pass in for the function
      // the child actor provider class that will send a message to the
      // the test probe then will look for this message and it if finds when the poison pill is sent then it means
      // that it has created the a0ctor
      //mongoListener.ref ! PoisonPill // I need to then create a mongo actor and then get a reference to it, once I get a reference to it
      // then I pass that to the above retry manager - once I get that, then I send a poison pill to that mongo actor
      // then the actor system will receive a terminated message
      // once it receives a terminated message it will then instantiate the new mongo actor via the child provider class which then
      // send a message saying that it has created an actor
      // then some actor, maybe, the test-actor can do the expectMsg

      //val childMaker: ChildMaker = new ChildMaker(self)
      //val child                  = childMaker.newChild()

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
