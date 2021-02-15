/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.upscan._
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultUpscanRepository])
trait UpscanRepository {

  def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def select(
    uploadReference: UploadReference
  ): EitherT[Future, Error, Option[UpscanUpload]]

  def update(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def selectAll(
    uploadReference: List[UploadReference]
  ): EitherT[Future, Error, List[UpscanUpload]]

}

@Singleton
class DefaultUpscanRepository @Inject() (mongo: ReactiveMongoComponent, config: Configuration)(implicit
  val ec: ExecutionContext
) extends ReactiveRepository[UpscanUpload, BSONObjectID](
      collectionName = "upscan",
      mongo = mongo.mongoConnector.db,
      UpscanUpload.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with UpscanRepository
    with CacheRepository[UpscanUpload] {

  override val cacheTtlIndexName: String = "upscan-cache-ttl"

  override val objName: String = "upscan"

  val cacheTtl: FiniteDuration = config.underlying.get[FiniteDuration]("mongodb.upscan.expiry-time").value

  override def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    EitherT(
      set(
        upscanUpload.uploadReference.value,
        upscanUpload,
        Some(upscanUpload.uploadedOn)
      )
    )

  override def select(
    uploadReference: UploadReference
  ): EitherT[Future, Error, Option[UpscanUpload]] =
    EitherT(find(uploadReference.value))

  override def update(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    EitherT(
      set(
        uploadReference.value,
        upscanUpload,
        Some(upscanUpload.uploadedOn)
      )
    )

  override def selectAll(
    uploadReference: List[UploadReference]
  ): EitherT[Future, Error, List[UpscanUpload]] =
    EitherT(findAll(uploadReference.map(_.value)))

}
