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

import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.util.{ByteString, Timeout}
import cats.data.EitherT
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.Configuration
import play.api.test.Helpers.await
import uk.gov.hmrc.cgtpropertydisposals.connectors.UpscanConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanFileDescriptor.UpscanFileDescriptorStatus.UPLOADED
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanStatus.READY
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{FileDescriptorId, UpscanCallBack, UpscanFileDescriptor, UpscanSnapshot}
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.{UpscanCallBackRepository, UpscanFileDescriptorRepository}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class UpscanServiceSpec extends WordSpec with Matchers with MockFactory {

  val config = Configuration(
    ConfigFactory.parseString(
      """
        | microservice.services {
        |    upscan-initiate {
        |       max-uploads = 5
        |       s3url.expiry-time = 7
        |       dms = {
        |           classification-type = "queue-name"
        |           business-area = "cgt"
        |       }
        |    }
        | }
        |""".stripMargin
    )
  )

  implicit val timeout: Timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))

  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global

  val mockUpscanFileDescriptorRepository: UpscanFileDescriptorRepository = mock[UpscanFileDescriptorRepository]
  val mockUpscanCallBackRepository: UpscanCallBackRepository             = mock[UpscanCallBackRepository]
  val mockUpscanConnector: UpscanConnector                               = mock[UpscanConnector]

  val service = new UpscanServiceImpl(
    mockUpscanConnector,
    mockUpscanFileDescriptorRepository,
    mockUpscanCallBackRepository,
    config
  )

  def mockStoreUpscanFileDescriptor(upscanFileDescriptor: UpscanFileDescriptor)(
    response: Either[Error, Unit]
  ) =
    (mockUpscanFileDescriptorRepository
      .insert(_: UpscanFileDescriptor))
      .expects(upscanFileDescriptor)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockStoreUpscanCallBackRepository(upscanCallBack: UpscanCallBack)(
    response: Either[Error, Unit]
  ) =
    (mockUpscanCallBackRepository
      .insert(_: UpscanCallBack))
      .expects(upscanCallBack)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockDownloadFile(upscanCallBack: UpscanCallBack)(
    response: Either[Error, FileAttachment]
  ) =
    (mockUpscanConnector
      .downloadFile(_: UpscanCallBack))
      .expects(upscanCallBack)
      .returning(Future[Either[Error, FileAttachment]](response))

  def mockUpdateFileDescriptorStatus(upscanFileDescriptor: UpscanFileDescriptor)(
    response: Either[Error, Boolean]
  ) =
    (mockUpscanFileDescriptorRepository
      .updateUpscanUploadStatus(_: UpscanFileDescriptor))
      .expects(upscanFileDescriptor)
      .returning(EitherT[Future, Error, Boolean](Future.successful(response)))

  def mockGetUpscanSnapshot(cgtReference: CgtReference)(
    response: Either[Error, List[UpscanFileDescriptor]]
  ) =
    (mockUpscanFileDescriptorRepository
      .getAll(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT[Future, Error, List[UpscanFileDescriptor]](Future.successful(response)))

  def mockGetFileDescriptor(fileDescriptorId: FileDescriptorId)(
    response: Either[Error, Option[UpscanFileDescriptor]]
  ) =
    (mockUpscanFileDescriptorRepository
      .get(_: FileDescriptorId))
      .expects(fileDescriptorId)
      .returning(EitherT[Future, Error, Option[UpscanFileDescriptor]](Future.successful(response)))

  def mockDownloadS3Urls(upscanCallBack: UpscanCallBack)(
    response: Either[Error, FileAttachment]
  ) =
    (mockUpscanConnector
      .downloadFile(_: UpscanCallBack))
      .expects(upscanCallBack)
      .returning(Future[Either[Error, FileAttachment]](response))

  val cgtReference         = sample[CgtReference]
  val ts                   = java.time.Instant.ofEpochSecond(1000)
  val upscanFileDescriptor = sample[UpscanFileDescriptor]
  val upscanCallBack       = sample[UpscanCallBack]
  val s3Url                = "http://aws.s3.com/file"

  "Upscan Service" when {

    "it receives a request to update the file descriptor status" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockUpdateFileDescriptorStatus(upscanFileDescriptor)(Left(Error("Connection error")))
          await(service.updateUpscanFileDescriptorStatus(upscanFileDescriptor).value).isLeft shouldBe true
        }
      }
      "return true" when {
        "it successfully updates the data" in {
          mockUpdateFileDescriptorStatus(upscanFileDescriptor)(Right(true))
          await(service.updateUpscanFileDescriptorStatus(upscanFileDescriptor).value) shouldBe Right(true)
        }
      }
    }

    "it receives a request to get upscan snapshot information" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockGetUpscanSnapshot(cgtReference)(Left(Error("Connection error")))
          await(service.getUpscanSnapshot(cgtReference).value).isLeft shouldBe true
        }
      }
      "return a list of upscan file descriptors" when {
        "it successfully updates the data" in {
          mockGetUpscanSnapshot(cgtReference)(
            Right(List(upscanFileDescriptor.copy(status = UPLOADED, timestamp = LocalDateTime.now())))
          )
          await(service.getUpscanSnapshot(cgtReference).value) shouldBe Right(UpscanSnapshot(1))
        }
      }
    }

    "it receives a request to store upscan file descriptor data" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockStoreUpscanFileDescriptor(upscanFileDescriptor)(Left(Error("Connection error")))
          await(service.storeFileDescriptorData(upscanFileDescriptor).value).isLeft shouldBe true
        }
      }
      "return unit" when {
        "it successfully stores the data" in {
          mockStoreUpscanFileDescriptor(upscanFileDescriptor)(Right(()))
          await(service.storeFileDescriptorData(upscanFileDescriptor).value) shouldBe Right(())
        }
      }
    }
    "it receives a request to store upscan call back data" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockStoreUpscanCallBackRepository(upscanCallBack)(Left(Error("Connection error")))
          await(service.saveCallBackData(upscanCallBack).value).isLeft shouldBe true
        }
      }
      "return unit" when {
        "it successfully stores the data" in {
          mockStoreUpscanCallBackRepository(upscanCallBack)(Right(()))
          await(service.saveCallBackData(upscanCallBack).value) shouldBe Right(())
        }
      }
    }

    "it receives a request to get file descriptor " must {
      "return an error" when {
        "there is a mongo exception" in {
          mockStoreUpscanCallBackRepository(upscanCallBack)(Left(Error("Connection error")))
          await(service.saveCallBackData(upscanCallBack).value).isLeft shouldBe true
        }
      }
      "return an upscan file descriptor" when {
        "it successfully stores the data" in {
          mockStoreUpscanCallBackRepository(upscanCallBack)(Right(()))
          await(service.saveCallBackData(upscanCallBack).value) shouldBe Right(())
        }
      }
    }

    "it receives a request to get file descriptor information" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockGetFileDescriptor(FileDescriptorId("id"))(Left(Error("Connection error")))
          await(service.getFileDescriptor(cgtReference, FileDescriptorId("id")).value).isLeft shouldBe true
        }
      }
      "return an upscan file descriptor" when {
        "it successfully stores the data" in {
          mockGetFileDescriptor(FileDescriptorId("id"))(Right(Some(upscanFileDescriptor)))
          await(service.getFileDescriptor(cgtReference, FileDescriptorId("id")).value) shouldBe Right(
            Some(upscanFileDescriptor)
          )
        }
      }
    }

    "it receives a request to download a S3 file" must {
      "return an error" when {
        "all upscan call backs have not been received" in {
          await(service.downloadFilesFromS3(UpscanSnapshot(3), List(upscanCallBack)).value).isLeft shouldBe true
        }
      }
      "return a file attachment" when {
        "it successfully downloads the file" in {
          mockDownloadS3Urls(upscanCallBack.copy(fileStatus = READY))(
            Right(FileAttachment(UUID.randomUUID().toString, "filename", Some("pdf"), ByteString(1)))
          )
          await(service.downloadFilesFromS3(UpscanSnapshot(1), List(upscanCallBack.copy(fileStatus = READY))).value).isRight shouldBe true
        }
      }
    }
  }
}
