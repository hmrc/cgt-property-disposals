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
import java.util.concurrent.TimeUnit

import org.scalacheck.ScalacheckShapeless._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.EitherT
import com.miguno.akka.testing.VirtualTime
import org.scalacheck.{Arbitrary, Gen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.RetryTaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryMongoActor.{DeleteTaxEnrolmentRetryRequest, GetAllNonFailedTaxEnrolmentRetryRequests, UpdateTaxEnrolmentRetryStatusToFail}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxEnrolmentRequest, sample}
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.http.HttpResponse

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class TaxEnrolmentRetryMongoActorSpec
    extends TestKit(ActorSystem())
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockFactory {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  val time: VirtualTime = new VirtualTime()

  val mockTaxEnrolmentRepository = mock[TaxEnrolmentRetryRepository]

  val maxStaggerTime = 1

  val taxEnrolmentRetryMongoActor =
    system.actorOf(TaxEnrolmentRetryMongoActor.props(time.scheduler, mockTaxEnrolmentRepository, maxStaggerTime))
  val http4xxResponse = HttpResponse(403, None, Map.empty, None)
  val http5xxResponse = HttpResponse(502, None, Map.empty, None)

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

  implicit val localDateArb: Arbitrary[LocalDateTime] = Arbitrary.apply[LocalDateTime](Gen.const(LocalDateTime.now()))
  val taxEnrolmentRequest                             = sample[TaxEnrolmentRequest]

  "A Tax Enrolment Mongo actor" must {

    "not reply back with a message when there is an error recovering the tax enrolment requests from mongo" in {
      mockGetAllNonFailedEnrolmentRequests()(Left(Error("Error recovering tax enrolment retry requests")))
      taxEnrolmentRetryMongoActor ! GetAllNonFailedTaxEnrolmentRetryRequests
      expectNoMessage()
    }

    //TODO: fix
//    "reply back with a message when it has recovered tax enrolment requests from mongo" in {
//      mockGetAllNonFailedEnrolmentRequests()(Right(List(taxEnrolmentRequest)))
//      taxEnrolmentRetryMongoActor ! GetAllNonFailedTaxEnrolmentRetryRequests
//      time.advance(FiniteDuration(2, TimeUnit.SECONDS))
//      expectMsg(
//        RetryTaxEnrolmentRequest(
//          taxEnrolmentRequest.copy(numOfRetries = 0, timeToNextRetryInSeconds = 0, currentElapsedTimeInSeconds = 0)
//        )
//      )
//    }

    "not reply back with a message when there is an error updating the tax enrolment retry status" in {
      mockUpdateStatusToFail(taxEnrolmentRequest.userId)(Left(Error("Error updating status")))
      taxEnrolmentRetryMongoActor ! UpdateTaxEnrolmentRetryStatusToFail(taxEnrolmentRequest)
      expectNoMessage()
    }

    "do not reply back with a message when updating the tax enrolment retry status is a success" in {
      mockUpdateStatusToFail(taxEnrolmentRequest.userId)(Right(Some(taxEnrolmentRequest)))
      taxEnrolmentRetryMongoActor ! UpdateTaxEnrolmentRetryStatusToFail(taxEnrolmentRequest)
      expectNoMessage()
    }

    "do not reply back with a message when updating the tax enrolment retry status is not a success" in {
      mockUpdateStatusToFail(taxEnrolmentRequest.userId)(Right(None))
      taxEnrolmentRetryMongoActor ! UpdateTaxEnrolmentRetryStatusToFail(taxEnrolmentRequest)
      expectNoMessage()
    }

    "not reply back with a message when there is an error deleting the tax enrolment retry record" in {
      mockDeleteTaxEnrolment(taxEnrolmentRequest.userId)(Left(Error("Error deleting record")))
      taxEnrolmentRetryMongoActor ! DeleteTaxEnrolmentRetryRequest(taxEnrolmentRequest)
      expectNoMessage()
    }

    "do not reply back with a message when deleting the tax enrolment retry status is a success" in {
      mockDeleteTaxEnrolment(taxEnrolmentRequest.userId)(Right(1))
      taxEnrolmentRetryMongoActor ! DeleteTaxEnrolmentRetryRequest(taxEnrolmentRequest)
      expectNoMessage()
    }

    "do not reply back with a message when deleting the tax enrolment retry status is not a success" in {
      mockDeleteTaxEnrolment(taxEnrolmentRequest.userId)(Right(0))
      taxEnrolmentRetryMongoActor ! DeleteTaxEnrolmentRetryRequest(taxEnrolmentRequest)
      expectNoMessage()
    }
  }
}
