/*
 * Copyright 2022 HM Revenue & Customs
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

///*
// * Copyright 2022 HM Revenue & Customs
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
package uk.gov.hmrc.cgtpropertydisposals.service.upscan

import java.util.UUID
import java.util.concurrent.TimeUnit
import akka.util.{ByteString, Timeout}
import cats.data.EitherT
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.test.Helpers.await
import uk.gov.hmrc.cgtpropertydisposals.connectors.dms.S3Connector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UploadReference, UpscanUpload, UpscanUploadWrapper}
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.UpscanRepository
import uk.gov.hmrc.mongo.test.CleanMongoCollectionSupport

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class UpscanServiceSpec extends AnyWordSpec with Matchers with MockFactory with CleanMongoCollectionSupport {

  implicit val timeout: Timeout                           = Timeout(FiniteDuration(5, TimeUnit.SECONDS))
  implicit val executionContext: ExecutionContextExecutor = ExecutionContext.global
  val mockUpscanRepository                                = mock[UpscanRepository]
  val mockUpscanConnector                                 = mock[S3Connector]
  val service                                             = new UpscanServiceImpl(mockUpscanRepository, mockUpscanConnector)

  def mockStoreUpscanUpload(upscanUpload: UpscanUpload)(
    response: Either[Error, Unit]
  ) =
    (mockUpscanRepository
      .insert(_: UpscanUpload))
      .expects(upscanUpload)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockReadUpscanUpload(uploadReference: UploadReference)(
    response: Either[Error, Option[UpscanUploadWrapper]]
  ) =
    (mockUpscanRepository
      .select(_: UploadReference))
      .expects(uploadReference)
      .returning(EitherT[Future, Error, Option[UpscanUploadWrapper]](Future.successful(response)))

  def mockReadUpscanUploads(uploadReferences: List[UploadReference])(
    response: Either[Error, List[UpscanUploadWrapper]]
  ) =
    (mockUpscanRepository
      .selectAll(_: List[UploadReference]))
      .expects(uploadReferences)
      .returning(EitherT[Future, Error, List[UpscanUploadWrapper]](Future.successful(response)))

  def mockUpdateUpscanUpload(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  )(
    response: Either[Error, Unit]
  ) =
    (mockUpscanRepository
      .update(_: UploadReference, _: UpscanUpload))
      .expects(uploadReference, upscanUpload)
      .returning(EitherT[Future, Error, Unit](Future.successful(response)))

  def mockDownloadFile(upscanSuccess: UpscanSuccess)(
    response: Either[Error, FileAttachment]
  ) =
    (mockUpscanConnector
      .downloadFile(_: UpscanSuccess))
      .expects(upscanSuccess)
      .returning(Future[Either[Error, FileAttachment]](response))

  val upscanUpload        = sample[UpscanUpload]
  val upscanUploadWrapper = sample[UpscanUploadWrapper]

  "Upscan Service" when {

    "it receives a request to store an upscan upload" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockStoreUpscanUpload(upscanUpload)(Left(Error("Connection error")))
          await(service.storeUpscanUpload(upscanUpload).value).isLeft shouldBe true
        }
      }
      "return unit" when {
        "it successfully stores the data" in {
          mockStoreUpscanUpload(upscanUpload)(Right(()))
          await(service.storeUpscanUpload(upscanUpload).value) shouldBe Right(())
        }
      }
    }

    "it receives a request to read a upscan upload" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockReadUpscanUpload(upscanUpload.uploadReference)(Left(Error("Connection error")))
          await(service.readUpscanUpload(upscanUpload.uploadReference).value).isLeft shouldBe true
        }
      }
      "return some upscan upload" when {
        "it successfully reads the data" in {
          mockReadUpscanUpload(upscanUpload.uploadReference)(Right(Some(upscanUploadWrapper)))
          await(service.readUpscanUpload(upscanUpload.uploadReference).value) shouldBe Right(Some(upscanUploadWrapper))
        }
      }
    }

    "it receives a request to read a upscan uploads" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockReadUpscanUploads(List(upscanUpload.uploadReference))(Left(Error("Connection error")))
          await(service.readUpscanUploads(List(upscanUpload.uploadReference)).value).isLeft shouldBe true
        }
      }
      "return some upscan upload" when {
        "it successfully reads the data" in {
          mockReadUpscanUploads(List(upscanUpload.uploadReference))(Right(List(upscanUploadWrapper)))
          await(service.readUpscanUploads(List(upscanUpload.uploadReference)).value) shouldBe Right(
            List(upscanUploadWrapper)
          )
        }
      }
    }

    "it receives a request to update an upscan upload" must {
      "return an error" when {
        "there is a mongo exception" in {
          mockUpdateUpscanUpload(upscanUpload.uploadReference, upscanUpload)(Left(Error("Connection error")))
          await(service.updateUpscanUpload(upscanUpload.uploadReference, upscanUpload).value).isLeft shouldBe true
        }
      }
      "return some upscan upload" when {
        "it successfully stores the data" in {
          mockUpdateUpscanUpload(upscanUpload.uploadReference, upscanUpload)(Right(()))
          await(service.updateUpscanUpload(upscanUpload.uploadReference, upscanUpload).value) shouldBe Right(())
        }
      }
    }

    "it receives a request to download a S3 file" must {

      val upscanSuccess1  = sample[UpscanSuccess]
      val upscanSuccess2  = sample[UpscanSuccess]
      val fileAttachment1 = FileAttachment(UUID.randomUUID().toString, "filename", Some("pdf"), Seq(ByteString(1)))
      val fileAttachment2 = FileAttachment(UUID.randomUUID().toString, "filename2", Some("pdf"), Seq(ByteString(2)))

      "return an error" when {

        "some of the downloads fail" in {
          mockDownloadFile(upscanSuccess1)(Right(fileAttachment1))
          mockDownloadFile(upscanSuccess2)(Left(Error("")))

          await(service.downloadFilesFromS3(List(upscanSuccess1, upscanSuccess2))) shouldBe List(
            Right(fileAttachment1),
            Left(Error(""))
          )
        }
      }
      "return file attachments" when {
        "it successfully downloads the file" in {
          mockDownloadFile(upscanSuccess1)(Right(fileAttachment1))
          mockDownloadFile(upscanSuccess2)(Right(fileAttachment2))

          await(service.downloadFilesFromS3(List(upscanSuccess1, upscanSuccess2))) shouldBe List(
            Right(fileAttachment1),
            Right(fileAttachment2)
          )

        }
      }
    }

  }
}
