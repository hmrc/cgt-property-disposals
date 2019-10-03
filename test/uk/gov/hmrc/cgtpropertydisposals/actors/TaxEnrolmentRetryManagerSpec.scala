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

import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.EitherT
import com.miguno.akka.testing.VirtualTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.{QueueTaxEnrolmentRequest, RetryTaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Address.UkAddress
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest.TaxEnrolmentFailed
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.repositories.{DefaultTaxEnrolmentRetryRepository, MongoSupport, TaxEnrolmentRetryRepository}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class TaxEnrolmentRetryManagerSpec
    extends TestKit(ActorSystem())
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MongoSupport
    with MockFactory {

  override def beforeEach: Unit =
    mongo().drop()

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  val connector         = mock[TaxEnrolmentConnector]
  val mockRepo          = mock[TaxEnrolmentRetryRepository]
  val repository        = new DefaultTaxEnrolmentRetryRepository(reactiveMongoComponent)
  val time: VirtualTime = new VirtualTime()

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val minBackoff: FiniteDuration                            = FiniteDuration(1, TimeUnit.SECONDS)
  val maxBackoff: FiniteDuration                            = FiniteDuration(5, TimeUnit.SECONDS)
  val numberOfRetriesBeforeDoublingInitialWait: Int         = 1
  val maxAllowableElapsedTime: FiniteDuration               = FiniteDuration(30, TimeUnit.SECONDS)
  val maxWaitTimeForRetryingRecoveredEnrolmentRequests: Int = 2

  def mockAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(response: Either[Error, HttpResponse]) =
    (connector
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT(Future.successful(response)))

  def mockUpdate(userId: String)(response: Either[Error, Option[TaxEnrolmentRequest]]) =
    (mockRepo
      .updateStatusToFail(_: String))
      .expects(userId)
      .returning(EitherT(Future.successful(response)))

  def mockGetAll()(response: Either[Error, List[TaxEnrolmentRequest]]) =
    (mockRepo.getAllNonFailedEnrolmentRequests _)
      .expects()
      .returning(EitherT(Future.successful(response)))

  def mockInsert(taxEnrolmentRequest: TaxEnrolmentRequest)(response: Either[Error, Boolean]) =
    (mockRepo
      .insert(_: TaxEnrolmentRequest))
      .expects(taxEnrolmentRequest)
      .returning(EitherT(Future.successful(response)))

  def mockGet(userId: String)(response: Either[Error, Option[TaxEnrolmentRequest]]) =
    (mockRepo
      .get(_: String))
      .expects(userId)
      .returning(EitherT(Future.successful(response)))

  val taxEnrolmentRequest =
    TaxEnrolmentRequest("userId-1", 1, "test-cgt-reference", UkAddress("line1", None, None, None, "BN11 3JB"))

  "A Tax Enrolment Retry Manager actor" must {

    "recover all tax enrolments records from mongo on start up" in {
      await(repository.insert(taxEnrolmentRequest).value).isRight shouldBe true
      system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            minBackoff,
            maxBackoff,
            numberOfRetriesBeforeDoublingInitialWait,
            maxAllowableElapsedTime,
            maxWaitTimeForRetryingRecoveredEnrolmentRequests,
            connector,
            repository,
            time.scheduler
          )
        )
      )
      await(repository.get(taxEnrolmentRequest.ggCredId).value).shouldBe(Right(Some(taxEnrolmentRequest)))
    }

    "remove the request from db if the tax enrolment request was successful" in {
      mockAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(204)))

      val manager = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            minBackoff,
            maxBackoff,
            numberOfRetriesBeforeDoublingInitialWait,
            maxAllowableElapsedTime,
            maxWaitTimeForRetryingRecoveredEnrolmentRequests,
            connector,
            repository,
            time.scheduler
          )
        )
      )

      manager ! QueueTaxEnrolmentRequest(taxEnrolmentRequest)

      Thread.sleep(1000) // wait for akka system to do its work

      await(repository.get(taxEnrolmentRequest.ggCredId).value).shouldBe(Right(None))
    }

    "not remove the request from db if the response from the tax enrolment service is a 5xx" in {
      mockAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(500)))

      val manager = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            minBackoff,
            maxBackoff,
            numberOfRetriesBeforeDoublingInitialWait,
            maxAllowableElapsedTime,
            maxWaitTimeForRetryingRecoveredEnrolmentRequests,
            connector,
            repository,
            time.scheduler
          )
        )
      )

      manager ! QueueTaxEnrolmentRequest(taxEnrolmentRequest)

      Thread.sleep(1000) // wait for akka system to do its work

      await(repository.get(taxEnrolmentRequest.ggCredId).value)
        .shouldBe(Right(Some(taxEnrolmentRequest)))
    }

    "not remove the request from db if the response from the tax enrolment service is an exception" in {
      mockAllocateEnrolment(taxEnrolmentRequest)(Left(Error("Connection Timeout")))

      val manager = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            minBackoff,
            maxBackoff,
            numberOfRetriesBeforeDoublingInitialWait,
            maxAllowableElapsedTime,
            maxWaitTimeForRetryingRecoveredEnrolmentRequests,
            connector,
            repository,
            time.scheduler
          )
        )
      )

      manager ! QueueTaxEnrolmentRequest(taxEnrolmentRequest)

      Thread.sleep(1000) // wait for akka system to do its work

      await(repository.get(taxEnrolmentRequest.ggCredId).value)
        .shouldBe(Right(Some(taxEnrolmentRequest)))
    }

    "update the record to failed state if the elapsed time has exceeded the max elapsed time" in {

      await(repository.insert(taxEnrolmentRequest).value).isRight shouldBe true

      val manager = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            minBackoff,
            maxBackoff,
            numberOfRetriesBeforeDoublingInitialWait,
            maxAllowableElapsedTime,
            maxWaitTimeForRetryingRecoveredEnrolmentRequests,
            connector,
            repository,
            time.scheduler
          )
        )
      )

      manager ! RetryTaxEnrolmentRequest(taxEnrolmentRequest, 0, 0, 100000)

      Thread.sleep(1000) // wait for akka system to do its work

      await(repository.get(taxEnrolmentRequest.ggCredId).value)
        .shouldBe(Right(Some(taxEnrolmentRequest.copy(status = TaxEnrolmentFailed))))
    }

    "assert that the update did not occur if the the db query fails" in {

      mockGetAll()(Right(List.empty))

      mockUpdate("userId-1")(Left(Error("Exception")))

      mockGet(taxEnrolmentRequest.ggCredId)(Right(Some(taxEnrolmentRequest)))

      val manager = system.actorOf(
        Props(
          new TaxEnrolmentRetryManager(
            minBackoff,
            maxBackoff,
            numberOfRetriesBeforeDoublingInitialWait,
            maxAllowableElapsedTime,
            maxWaitTimeForRetryingRecoveredEnrolmentRequests,
            connector,
            repository,
            time.scheduler
          )
        )
      )

      await(repository.insert(taxEnrolmentRequest).value).isRight shouldBe true

      manager ! RetryTaxEnrolmentRequest(taxEnrolmentRequest, 0, 0, 100000)

      Thread.sleep(1000) // wait for akka system to do its work

      await(mockRepo.get(taxEnrolmentRequest.ggCredId).value)
        .shouldBe(Right(Some(taxEnrolmentRequest)))
    }

  }

}
