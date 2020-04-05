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

package uk.gov.hmrc.cgtpropertydisposals.service.upscan

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.connectors.UpscanConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UploadReference, UpscanUpload}
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.UpscanRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UpscanServiceImpl])
trait UpscanService {

  def storeUpscanUpload(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def readUpscanUpload(
    uploadReference: UploadReference
  ): EitherT[Future, Error, Option[UpscanUpload]]

  def readUpscanUploads(
    uploadReferences: List[UploadReference]
  ): EitherT[Future, Error, List[UpscanUpload]]

  def updateUpscanUpload(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def downloadFilesFromS3(
    upscanSuccesses: List[UpscanSuccess]
  ): Future[List[Either[Error, FileAttachment]]]

}

@Singleton
class UpscanServiceImpl @Inject() (
  upscanRepository: UpscanRepository,
  upscanConnector: UpscanConnector
)(implicit ec: ExecutionContext)
    extends UpscanService
    with Logging {

  override def storeUpscanUpload(upscanUpload: UpscanUpload): EitherT[Future, Error, Unit] =
    upscanRepository.insert(upscanUpload)

  override def readUpscanUpload(
    uploadReference: UploadReference
  ): EitherT[Future, Error, Option[UpscanUpload]] =
    upscanRepository.select(uploadReference)

  override def updateUpscanUpload(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    upscanRepository.update(uploadReference, upscanUpload)

  override def readUpscanUploads(
    uploadReferences: List[UploadReference]
  ): EitherT[Future, Error, List[UpscanUpload]] =
    upscanRepository.selectAll(uploadReferences)

  override def downloadFilesFromS3(
    upscanSuccesses: List[UpscanSuccess]
  ): Future[List[Either[Error, FileAttachment]]] =
    Future.traverse(upscanSuccesses)(url => upscanConnector.downloadFile(url))

}
