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

package uk.gov.hmrc.cgtpropertydisposals.service.dms

import cats.data.EitherT
import cats.instances.future._
import com.typesafe.config.ConfigFactory
import org.apache.pekko.actor.{ActorRef, ActorSystem}
import org.apache.pekko.testkit.{TestKit, TestProbe}
import org.bson.types.ObjectId
import org.mockito.IdiomaticMockito
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{B64Html, DmsEnvelopeId}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, UUIDGenerator}
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionPoller.OnCompleteHandler
import uk.gov.hmrc.cgtpropertydisposals.util.FileIOExecutionContext
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.{Failed, PermanentlyFailed, Succeeded, ToDo}
import uk.gov.hmrc.mongo.workitem.{ProcessingStatus, ResultStatus, WorkItem}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.util.UUID
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class DmsSubmissionPollerSpec
    extends TestKit(ActorSystem.create("dms-submission-poller"))
    with AnyWordSpecLike
    with Matchers
    with IdiomaticMockito
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

  private val mockUUIDGenerator = mock[UUIDGenerator]
  val formIdConst               = "CGTSUBMITDOC"

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val config = Configuration(
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

  private val mockDmsSubmissionService                                     = mock[DmsSubmissionService]
  implicit val dmsSubmissionPollerExecutionContext: FileIOExecutionContext = new FileIOExecutionContext(system)
  val servicesConfig                                                       = new ServicesConfig(config)

  private def mockDmsSubmissionRequestDequeue()(response: Either[Error, Option[WorkItem[DmsSubmissionRequest]]]) =
    mockDmsSubmissionService.dequeue.returns(EitherT.fromEither[Future](response))

  private def mockSubmitToDms(
    html: B64Html,
    formBundleId: String,
    cgtReference: CgtReference,
    completeReturn: CompleteReturn
  )(response: Either[Error, DmsEnvelopeId]) =
    mockDmsSubmissionService
      .submitToDms(html, formBundleId, cgtReference, completeReturn)
      .returns(EitherT.fromEither(response))

  private def mockSetProcessingStatus(id: ObjectId, status: ProcessingStatus)(response: Either[Error, Boolean]) =
    mockDmsSubmissionService
      .setProcessingStatus(id, status)
      .returns(EitherT.fromEither[Future](response))

  private def mockSetResultStatus(id: ObjectId, status: ResultStatus)(response: Either[Error, Boolean]) =
    mockDmsSubmissionService
      .setResultStatus(id, status)
      .returns(EitherT.fromEither[Future](response))

  private def mockNextUUID(uuid: UUID) =
    mockUUIDGenerator.nextId().returns(uuid)

  "DMS Submission Poller" when {
    "it picks up a work item" must {
      "process the work item and set it to succeed if the dms submission is successful" in {
        val onCompleteListener = TestProbe()
        val workItem           = sample[WorkItem[DmsSubmissionRequest]].copy(failureCount = 0, status = ToDo)
        val id                 = UUID.randomUUID()

        mockDmsSubmissionRequestDequeue()(Right(Some(workItem)))
        mockNextUUID(id)
        mockSubmitToDms(
          workItem.item.html,
          formIdConst,
          workItem.item.cgtReference,
          workItem.item.completeReturn
        )(Right(DmsEnvelopeId("id")))
        mockSetResultStatus(workItem.id, Succeeded)(Right(true))

        val _ =
          new DmsSubmissionPoller(
            system,
            mockDmsSubmissionService,
            dmsSubmissionPollerExecutionContext,
            servicesConfig,
            new TestOnCompleteHandler(onCompleteListener.ref),
            mockUUIDGenerator
          )

        onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
      }
    }

    "process the work item and set it to fail if the dms submission is not successful" in {
      val onCompleteListener = TestProbe()

      val workItem = sample[WorkItem[DmsSubmissionRequest]].copy(failureCount = 0, status = ToDo)
      val id       = UUID.randomUUID()

      mockDmsSubmissionRequestDequeue()(Right(Some(workItem)))
      mockNextUUID(id)
      mockSubmitToDms(
        workItem.item.html,
        formIdConst,
        workItem.item.cgtReference,
        workItem.item.completeReturn
      )(Left(Error("some-error")))
      mockSetProcessingStatus(workItem.id, Failed)(Right(true))

      val _ =
        new DmsSubmissionPoller(
          system,
          mockDmsSubmissionService,
          dmsSubmissionPollerExecutionContext,
          servicesConfig,
          new TestOnCompleteHandler(onCompleteListener.ref),
          mockUUIDGenerator
        )

      onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
    }

    "process the work item and set it to permanently failed if failure count has been reached" in {
      val onCompleteListener = TestProbe()

      val workItem = sample[WorkItem[DmsSubmissionRequest]].copy(failureCount = 10, status = Failed)
      mockDmsSubmissionRequestDequeue()(Right(Some(workItem)))
      mockSetResultStatus(workItem.id, PermanentlyFailed)(Right(true))

      val _ =
        new DmsSubmissionPoller(
          system,
          mockDmsSubmissionService,
          dmsSubmissionPollerExecutionContext,
          servicesConfig,
          new TestOnCompleteHandler(onCompleteListener.ref),
          mockUUIDGenerator
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
            new TestOnCompleteHandler(onCompleteListener.ref),
            mockUUIDGenerator
          )

        onCompleteListener.expectMsg(TestOnCompleteHandler.Completed)
      }
    }
  }
}
