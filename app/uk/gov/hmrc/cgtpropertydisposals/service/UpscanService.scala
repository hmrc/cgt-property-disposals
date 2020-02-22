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

import cats.data.{EitherT, NonEmptyList}
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.Configs
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.connectors.UpscanConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanFileDescriptor.UpscanFileDescriptorStatus.UPLOADED
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{FileDescriptorId, UpscanCallBack, UpscanFileDescriptor, UpscanSnapshot}
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.{UpscanCallBackRepository, UpscanFileDescriptorRepository}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

import scala.concurrent.{ExecutionContext, Future}
@ImplementedBy(classOf[UpscanServiceImpl])
trait UpscanService {

  def getUpscanSnapshot(cgtReference: CgtReference): EitherT[Future, Error, UpscanSnapshot]

  def storeFileDescriptorData(fd: UpscanFileDescriptor): EitherT[Future, Error, Unit]

  def storeCallBackData(cb: UpscanCallBack): EitherT[Future, Error, Unit]

  def getFileDescriptor(
    cgtReference: CgtReference,
    fileDescriptorId: FileDescriptorId
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]]

  def getUpscanFileDescriptor(fileDescriptorId: FileDescriptorId): EitherT[Future, Error, Option[UpscanFileDescriptor]]

  def updateUpscanFileDescriptorStatus(upscanFileDescriptor: UpscanFileDescriptor): EitherT[Future, Error, Boolean]

  def getAllUpscanCallBacks(cgtReference: CgtReference): EitherT[Future, Error, List[UpscanCallBack]]

  def downloadFilesFromS3(
    snapshot: UpscanSnapshot,
    urls: List[UpscanCallBack]
  ): EitherT[Future, Error, List[Either[Error, FileAttachment]]]
}

@Singleton
class UpscanServiceImpl @Inject() (
  upscanConnector: UpscanConnector,
  upscanFileDescriptorRepository: UpscanFileDescriptorRepository,
  upscanCallBackRepository: UpscanCallBackRepository,
  configuration: Configuration
)(implicit executionContext: ExecutionContext)
    extends UpscanService
    with Logging {

  private def getUpscanInitiateConfig[A: Configs](key: String): A =
    configuration.underlying
      .get[A](s"microservice.services.upscan-initiate.$key")
      .value

  private val s3UrlExpiryTime: Long = getUpscanInitiateConfig[Long]("s3url.expiry-time")

  override def getUpscanSnapshot(
    cgtReference: CgtReference
  ): EitherT[Future, Error, UpscanSnapshot] =
    for {
      fds <- upscanFileDescriptorRepository.getAll(cgtReference)
      d   <- EitherT.fromEither[Future](computeUpscanSnapshot(fds))
    } yield d

  override def updateUpscanFileDescriptorStatus(
    upscanFileDescriptor: UpscanFileDescriptor
  ): EitherT[Future, Error, Boolean] =
    upscanFileDescriptorRepository.updateUpscanUploadStatus(upscanFileDescriptor)

  def getUpscanFileDescriptor(
    fileDescriptorId: FileDescriptorId
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]] =
    upscanFileDescriptorRepository.get(fileDescriptorId)

  override def storeFileDescriptorData(fd: UpscanFileDescriptor): EitherT[Future, Error, Unit] =
    upscanFileDescriptorRepository.insert(fd)

  override def storeCallBackData(cb: UpscanCallBack): EitherT[Future, Error, Unit] =
    upscanCallBackRepository.insert(cb)

  override def getFileDescriptor(
    cgtReference: CgtReference,
    fileDescriptorId: FileDescriptorId
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]] =
    upscanFileDescriptorRepository.get(fileDescriptorId)

  override def downloadFilesFromS3(
    snapshot: UpscanSnapshot,
    urls: List[UpscanCallBack]
  ): EitherT[Future, Error, List[Either[Error, FileAttachment]]] = {
    implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
    if ((snapshot.fileUploadCount === urls.size) && urls.forall(p => p.fileStatus === UPLOADED)) {
      val maybeNonEmptyList: Option[NonEmptyList[UpscanCallBack]] = NonEmptyList.fromList(urls)

      val maybeDownloads: Option[IO[NonEmptyList[Either[Error, FileAttachment]]]] = maybeNonEmptyList map { urls =>
        urls.parTraverse(url => upscanConnector.downloadFile(url))
      }

      val ss: Option[Future[List[Either[Error, FileAttachment]]]] =
        maybeDownloads.map(io => io.unsafeToFuture().map(s => s.toList))

      ss match {
        case Some(value) => EitherT.liftF[Future, Error, List[Either[Error, FileAttachment]]](value)
        case None        => EitherT.leftT[Future, List[Either[Error, FileAttachment]]](Error("failed to get some downloads"))
      }
    } else {
      EitherT.leftT[Future, List[Either[Error, FileAttachment]]](
        Error("All upscan callbacks have not been received or some files are infected")
      )
    }
  }

  private def computeUpscanSnapshot(upscanFileDescriptor: List[UpscanFileDescriptor]): Either[Error, UpscanSnapshot] = {
    val validFiles = upscanFileDescriptor
      .filter(fd => fd.timestamp.isAfter(LocalDateTime.now().minusDays(s3UrlExpiryTime)))
      .filter(fd => fd.status === UPLOADED)
    Right(
      UpscanSnapshot(
        validFiles.size
      )
    )
  }

  def getAllUpscanCallBacks(cgtReference: CgtReference): EitherT[Future, Error, List[UpscanCallBack]] =
    upscanCallBackRepository.getAll(cgtReference)

}
