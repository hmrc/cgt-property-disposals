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

import cats.data.EitherT
import cats.implicits._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.Configs
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.connectors.UpscanConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanStatus.READY
import uk.gov.hmrc.cgtpropertydisposals.models.upscan._
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.{UpscanCallBackRepository, UpscanFileDescriptorRepository}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

import scala.concurrent.{ExecutionContext, Future}
@ImplementedBy(classOf[UpscanServiceImpl])
trait UpscanService {

  def getUpscanSnapshot(draftReturnId: DraftReturnId): EitherT[Future, Error, UpscanSnapshot]

  def storeFileDescriptorData(fd: UpscanFileDescriptor): EitherT[Future, Error, Unit]

  def saveCallBackData(cb: UpscanCallBack): EitherT[Future, Error, Boolean]

  def getUpscanFileDescriptor(fileDescriptorId: FileDescriptorId): EitherT[Future, Error, Option[UpscanFileDescriptor]]

  def getAll(draftReturnId: DraftReturnId): EitherT[Future, Error, List[UpscanFileDescriptor]]

  def updateUpscanFileDescriptorStatus(upscanFileDescriptor: UpscanFileDescriptor): EitherT[Future, Error, Boolean]

  def getAllUpscanCallBacks(draftReturnId: DraftReturnId): EitherT[Future, Error, List[UpscanCallBack]]

  def downloadFilesFromS3(
    snapshot: UpscanSnapshot,
    urls: List[UpscanCallBack]
  ): EitherT[Future, Error, List[Either[Error, FileAttachment]]]

  def deleteFile(
    draftReturnId: DraftReturnId,
    upscanInitiateReference: UpscanInitiateReference
  ): EitherT[Future, Error, Unit]

  def deleteAllFiles(
    draftReturnId: DraftReturnId
  ): EitherT[Future, Error, Unit]

}

@Singleton
class UpscanServiceImpl @Inject() (
  upscanConnector: UpscanConnector,
  upscanFileDescriptorRepository: UpscanFileDescriptorRepository, //FIXME: rename this
  upscanCallBackRepository: UpscanCallBackRepository, //FIXME : remove this as we don't need it - we directly update the UpscanFileDescriptorRepo
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
    draftReturnId: DraftReturnId
  ): EitherT[Future, Error, UpscanSnapshot] =
    for {
      fileDescriptors <- upscanFileDescriptorRepository.getAll(draftReturnId)
      upscanSnapshot  <- EitherT.fromEither[Future](computeUpscanSnapshot(fileDescriptors))
    } yield upscanSnapshot

  override def updateUpscanFileDescriptorStatus(
    upscanFileDescriptor: UpscanFileDescriptor
  ): EitherT[Future, Error, Boolean] =
    upscanFileDescriptorRepository.updateUpscanUploadStatus(upscanFileDescriptor)

  def getUpscanFileDescriptor( //TODO : pass in draft return id as it needs to be keyed on that as well
    fileDescriptorId: FileDescriptorId
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]] =
    upscanFileDescriptorRepository.get(fileDescriptorId)

  def getAll( //TODO : pass in draft return id as it needs to be keyed on that as well
    draftReturnId: DraftReturnId
  ): EitherT[Future, Error, List[UpscanFileDescriptor]] =
    upscanFileDescriptorRepository.getAll(draftReturnId)

  override def storeFileDescriptorData(fd: UpscanFileDescriptor): EitherT[Future, Error, Unit] =
    upscanFileDescriptorRepository.insert(fd)

  override def saveCallBackData(cb: UpscanCallBack): EitherT[Future, Error, Boolean] =
    upscanFileDescriptorRepository.updateStatus(cb)

  override def downloadFilesFromS3(
    snapshot: UpscanSnapshot,
    urls: List[UpscanCallBack]
  ): EitherT[Future, Error, List[Either[Error, FileAttachment]]] =
    if ((snapshot.fileUploadCount === urls.size) && urls.forall(p => p.fileStatus === READY)) {
      EitherT[Future, Error, List[Either[Error, FileAttachment]]](
        Future.traverse(urls)(url => upscanConnector.downloadFile(url)).map { s =>
          Right(s)
        }
      )
    } else {
      EitherT.leftT(
        Error("All upscan callbacks have not been received or some files are infected")
      )
    }

  private def computeUpscanSnapshot(upscanFileDescriptor: List[UpscanFileDescriptor]): Either[Error, UpscanSnapshot] = {
    logger.info(s"stored upscan file descriptors: $upscanFileDescriptor ") //FIXME remove thos or make them better and the one below

    val validFiles = upscanFileDescriptor
      .filter(fd => fd.timestamp.isAfter(LocalDateTime.now().minusDays(s3UrlExpiryTime)))
      .filter(fd => fd.status === UpscanFileDescriptor.UpscanFileDescriptorStatus.READY)

    logger.info(s"filtered upscan file descriptors: $upscanFileDescriptor ")

    Right(
      UpscanSnapshot(
        validFiles.size
      )
    )
  }

  def getAllUpscanCallBacks(draftReturnId: DraftReturnId): EitherT[Future, Error, List[UpscanCallBack]] =
    upscanCallBackRepository.getAll(draftReturnId) //FIXME remove this

  override def deleteFile(
    draftReturnId: DraftReturnId,
    upscanInitiateReference: UpscanInitiateReference
  ): EitherT[Future, Error, Unit] = upscanFileDescriptorRepository.deleteFile(draftReturnId, upscanInitiateReference)

  override def deleteAllFiles(draftReturnId: DraftReturnId): EitherT[Future, Error, Unit] =
    upscanFileDescriptorRepository.deleteAllFiles(draftReturnId)
}
