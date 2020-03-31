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

package uk.gov.hmrc.cgtpropertydisposals.service

import java.util.concurrent.TimeUnit

import akka.util.{ByteString, Timeout}
import cats.data.EitherT
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.test.Helpers.await
import uk.gov.hmrc.cgtpropertydisposals.connectors.GFormConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanStatus.{FAILED, READY}
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanCallBack, UpscanSnapshot}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class DmsSubmissionServiceSpec() extends WordSpec with Matchers with MockFactory {

  implicit val timeout: Timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))

  val executionContext: ExecutionContextExecutor = ExecutionContext.global

  val mockGFormConnector         = mock[GFormConnector]
  val mockUpscanService          = mock[UpscanService]
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val config = Configuration(
    ConfigFactory.parseString(
      """
        | dms {
        |   queue-name = "queue-name"
        |   b64-business-area = "YnVzaW5lc3MtYXJlYQ=="
        | }
        |""".stripMargin
    )
  )

  def mockGetUpscanSnapshot(cgtReference: CgtReference)(
    response: Either[Error, UpscanSnapshot]
  ) =
    (mockUpscanService
      .getUpscanSnapshot(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT[Future, Error, UpscanSnapshot](Future.successful(response)))

  def mockGetAllUpscanCallBacks(cgtReference: CgtReference)(
    response: Either[Error, List[UpscanCallBack]]
  ) =
    (mockUpscanService
      .getAllUpscanCallBacks(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT[Future, Error, List[UpscanCallBack]](Future.successful(response)))

  def mockGFormSubmission(dmsSubmissionPayload: DmsSubmissionPayload)(
    response: Either[Error, EnvelopeId]
  ) =
    (mockGFormConnector
      .submitToDms(_: DmsSubmissionPayload)(_: HeaderCarrier))
      .expects(dmsSubmissionPayload, *)
      .returning(EitherT[Future, Error, EnvelopeId](Future.successful(response)))

  def mockDownloadS3Urls(upscanSnapshot: UpscanSnapshot, upscanCallBacks: List[UpscanCallBack])(
    response: Either[Error, List[Either[Error, FileAttachment]]]
  ) =
    (mockUpscanService
      .downloadFilesFromS3(_: UpscanSnapshot, _: List[UpscanCallBack]))
      .expects(upscanSnapshot, upscanCallBacks)
      .returning(EitherT[Future, Error, List[Either[Error, FileAttachment]]](Future.successful(response)))

  val dmsSubmissionService =
    new DefaultDmsSubmissionService(mockGFormConnector, mockUpscanService, config)(executionContext)

  "Dms Submission Service" when {

    "a dms file submission request is made" must {
      val cgtReference = sample[CgtReference]
      val dmsMetadata  = DmsMetadata("form-bundle-id", cgtReference.value, "queue-name", "business-area")

      "return an error" when {

        "there is an issue with the gform service" in {
          val upscanSnapshot       = UpscanSnapshot(1)
          val upscanCallBack       = UpscanCallBack(cgtReference, "reference", READY, Some("download-url"), Map.empty)
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
            mockGetAllUpscanCallBacks(cgtReference)(Right(List(upscanCallBack)))
            mockDownloadS3Urls(upscanSnapshot, List(upscanCallBack))(
              Right(fileAttachments.map(attachment => Right(attachment)))
            )
            mockGFormSubmission(dmsSubmissionPayload)(Left(Error("gForm service error")))
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }

        "all upscan call backs have not been received" in {
          val upscanSnapshot       = UpscanSnapshot(2)
          val upscanCallBack       = UpscanCallBack(cgtReference, "reference", READY, Some("download-url"), Map.empty)
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
            mockGetAllUpscanCallBacks(cgtReference)(Right(List(upscanCallBack)))
            mockDownloadS3Urls(upscanSnapshot, List(upscanCallBack))(
              Right(fileAttachments.map(attachment => Right(attachment)))
            )
            mockGFormSubmission(dmsSubmissionPayload)(Left(Error("gForm service error")))
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }

        "unable to retrieve the upscan snapshot" in {
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Left(Error("mongo error")))
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }

        "unable to retrieve the upscan call back information" in {
          val upscanSnapshot       = UpscanSnapshot(1)
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
            mockGetAllUpscanCallBacks(cgtReference)(Left(Error("mongo-error")))
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }

        "unable to download the files" in {
          val upscanSnapshot       = UpscanSnapshot(1)
          val upscanCallBack       = UpscanCallBack(cgtReference, "reference", READY, Some("download-url"), Map.empty)
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
            mockGetAllUpscanCallBacks(cgtReference)(Right(List(upscanCallBack)))
            mockDownloadS3Urls(upscanSnapshot, List(upscanCallBack))(Left(Error("network-error")))
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }

        "some of the downloads have failed" in {
          val upscanSnapshot       = UpscanSnapshot(1)
          val upscanCallBack       = UpscanCallBack(cgtReference, "reference", READY, Some("download-url"), Map.empty)
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
            mockGetAllUpscanCallBacks(cgtReference)(Right(List(upscanCallBack)))
            mockDownloadS3Urls(upscanSnapshot, List(upscanCallBack))(
              Right(fileAttachments.map(attachment => Left(Error("error downloading file"))))
            )
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }

        "some of the files are infected with viruses" in {
          val upscanSnapshot       = UpscanSnapshot(1)
          val upscanCallBack       = UpscanCallBack(cgtReference, "reference", FAILED, Some("download-url"), Map.empty)
          val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
          val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

          inSequence {
            mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
            mockGetAllUpscanCallBacks(cgtReference)(Right(List(upscanCallBack)))
            mockDownloadS3Urls(upscanSnapshot, List(upscanCallBack))(
              Right(fileAttachments.map(attachment => Left(Error("error downloading file"))))
            )
          }
          await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value).isLeft shouldBe true
        }
      }

      "return an envelope id when files have been successfully submitted to the gform service" in {
        val upscanSnapshot       = UpscanSnapshot(1)
        val upscanCallBack       = UpscanCallBack(cgtReference, "reference", READY, Some("download-url"), Map.empty)
        val fileAttachments      = List(FileAttachment("key", "filename", Some("pdf"), ByteString(1)))
        val dmsSubmissionPayload = DmsSubmissionPayload(B64Html("<html>"), fileAttachments, dmsMetadata)

        inSequence {
          mockGetUpscanSnapshot(cgtReference)(Right(upscanSnapshot))
          mockGetAllUpscanCallBacks(cgtReference)(Right(List(upscanCallBack)))
          mockDownloadS3Urls(upscanSnapshot, List(upscanCallBack))(
            Right(fileAttachments.map(attachment => Right(attachment)))
          )
          mockGFormSubmission(dmsSubmissionPayload)(Right(EnvelopeId("env-id")))
        }
        await(dmsSubmissionService.submitToDms(dmsSubmissionPayload.b64Html, cgtReference, "form-bundle-id").value) shouldBe Right(
          EnvelopeId(
            "env-id"
          )
        )
      }
    }
  }

}
