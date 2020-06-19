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

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{TestKit, TestProbe}
import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.Eventually
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import play.api.{Configuration, Mode}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.connectors.dms.GFormConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{B64Html, EnvelopeId}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.dms.DmsSubmissionRepo
import uk.gov.hmrc.cgtpropertydisposals.service.DmsSubmissionService
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionPoller.OnCompleteHandler
import uk.gov.hmrc.cgtpropertydisposals.service.upscan.UpscanService
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.{RunMode, ServicesConfig}
import uk.gov.hmrc.workitem._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class DmsSubmissionPollerSpec
    extends TestKit(ActorSystem.create("dms-submission-poller"))
    with WordSpecLike
    with Matchers
    with MockFactory
    with Eventually
    with BeforeAndAfterAll {

  object TestOnCompleteHandler {
    case object Completed
  }

  class TestOnCompleteHandler(reportTo: ActorRef) extends OnCompleteHandler {
    override def onComplete(): Unit = reportTo ! TestOnCompleteHandler.Completed
  }

  override def afterAll(): Unit =
    TestKit.shutdownActorSystem(system)

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

  val mockGFormConnector    = mock[GFormConnector]
  val mockUpscanService     = mock[UpscanService]
  val mockDmsSubmissionRepo = mock[DmsSubmissionRepo]

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val config = Configuration(
    ConfigFactory.parseString(
      """
        |dms {
        |    queue-name = "queue-name"
        |    b64-business-area = "YnVzaW5lc3MtYXJlYQ=="
        |    submission-poller {
        |        jitter-period = 1 millisecond
        |        initial-delay = 1 millisecond
        |        interval = 120 seconds
        |        failure-count-limit = 10
        |        in-progress-retry-after = 5000 # milliseconds as required by work-item-repo library
        |        mongo {
        |            ttl = 7 days
        |        }
        |    }
        |}
        |
        |""".stripMargin
    )
  )

  val mockDmsSubmissionService                     = mock[DmsSubmissionService]
  implicit val dmsSubmissionPollerExecutionContext = new DmsSubmissionPollerExecutionContext(system)
  val servicesConfig                               = new ServicesConfig(config, new RunMode(config, Mode.Test))

  def mockDmsSubmissionRequestDequeue()(response: Either[Error, Option[WorkItem[DmsSubmissionRequest]]]) =
    (mockDmsSubmissionService.dequeue _)
      .expects()
      .returning(EitherT.fromEither[Future](response))

  def mockSubmitToDms()(response: Either[Error, EnvelopeId]) =
    (mockDmsSubmissionService
      .submitToDms(_: B64Html, _: String, _: CgtReference, _: CompleteReturn)(_: HeaderCarrier))
      .expects(*, *, *, *, *)
      .returning(EitherT.fromEither(response))

  def mockSetProcessingStatus(id: BSONObjectID, status: ProcessingStatus)(response: Either[Error, Boolean]) =
    (mockDmsSubmissionService
      .setProcessingStatus(_: BSONObjectID, _: ProcessingStatus))
      .expects(id, status)
      .returning(EitherT.fromEither[Future](response))

  def mockSetResultStatus(id: BSONObjectID, status: ResultStatus)(response: Either[Error, Boolean]) =
    (mockDmsSubmissionService
      .setResultStatus(_: BSONObjectID, _: ResultStatus))
      .expects(id, status)
      .returning(EitherT.fromEither[Future](response))

  "DMS Submission Poller" when {
    "it picks up a work item" must {
      "process the work item and set it to succeed if the dms submission is successful" in {
        val onCompleteListener = TestProbe()
        val workItem           = sample[WorkItem[DmsSubmissionRequest]].copy(failureCount = 0, status = ToDo)
        inSequence {
          mockDmsSubmissionRequestDequeue()(Right(Some(workItem)))
          mockSubmitToDms()(Right(EnvelopeId("id")))
          mockSetResultStatus(workItem.id, Succeeded)(Right(true))
        }

        val _ =
          new DmsSubmissionPoller(
            system,
            mockDmsSubmissionService,
            dmsSubmissionPollerExecutionContext,
            servicesConfig,
            new TestOnCompleteHandler(onCompleteListener.ref)
          )

        onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
      }
    }

    "process the work item and set it to fail if the dms submission is not successful" in {
      val onCompleteListener = TestProbe()

      val workItem = sample[WorkItem[DmsSubmissionRequest]].copy(failureCount = 0, status = ToDo)
      inSequence {
        mockDmsSubmissionRequestDequeue()(Right(Some(workItem)))
        mockSubmitToDms()(Left(Error("some-error")))
        mockSetProcessingStatus(workItem.id, Failed)(Right(true))
      }

      val _ =
        new DmsSubmissionPoller(
          system,
          mockDmsSubmissionService,
          dmsSubmissionPollerExecutionContext,
          servicesConfig,
          new TestOnCompleteHandler(onCompleteListener.ref)
        )

      onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
    }

    "process the work item and set it to permanently failed if failure count has been reached" in {
      val onCompleteListener = TestProbe()

      val workItem = sample[WorkItem[DmsSubmissionRequest]].copy(failureCount = 10, status = Failed)
      inSequence {
        mockDmsSubmissionRequestDequeue()(Right(Some(workItem)))
        mockSetResultStatus(workItem.id, PermanentlyFailed)(Right(true))
      }

      val _ =
        new DmsSubmissionPoller(
          system,
          mockDmsSubmissionService,
          dmsSubmissionPollerExecutionContext,
          servicesConfig,
          new TestOnCompleteHandler(onCompleteListener.ref)
        )

      onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
    }

    "it polls and there are no work items" must {
      "do nothing" in {
        val onCompleteListener = TestProbe()

        mockDmsSubmissionRequestDequeue()(Right(None))
        val _ =
          new DmsSubmissionPoller(
            system,
            mockDmsSubmissionService,
            dmsSubmissionPollerExecutionContext,
            servicesConfig,
            new TestOnCompleteHandler(onCompleteListener.ref)
          )

        onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
      }
    }
  }
}
