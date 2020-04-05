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
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanReference, UpscanUpload}
import uk.gov.hmrc.cgtpropertydisposals.repositories.upscan.UpscanRepository
import uk.gov.hmrc.cgtpropertydisposals.util.Logging

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UpscanServiceImpl])
trait UpscanService {

  def storeUpscanUpload(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def readUpscanUpload(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference
  ): EitherT[Future, Error, Option[UpscanUpload]]

  def updateUpscanUpload(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

}

@Singleton
class UpscanServiceImpl @Inject() (
  upscanRepository: UpscanRepository
) extends UpscanService
    with Logging {

  override def storeUpscanUpload(upscanUpload: UpscanUpload): EitherT[Future, Error, Unit] =
    upscanRepository.insert(upscanUpload)

  override def readUpscanUpload(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference
  ): EitherT[Future, Error, Option[UpscanUpload]] =
    upscanRepository.select(draftReturnId, upscanReference)

  override def updateUpscanUpload(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    upscanRepository.update(draftReturnId, upscanReference, upscanUpload)

}
