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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala.model.Filters.in
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.upscan._
import uk.gov.hmrc.cgtpropertydisposals.repositories.{CacheRepository, CurrentInstant}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[DefaultUpscanRepository])
trait UpscanRepository {
  def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def select(
    uploadReference: UploadReference
  ): EitherT[Future, Error, Option[UpscanUploadWrapper]]

  def update(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

  def selectAll(
    uploadReference: List[UploadReference]
  ): EitherT[Future, Error, List[UpscanUploadWrapper]]
}

@Singleton
class DefaultUpscanRepository @Inject() (mongo: MongoComponent, config: Configuration, clock: CurrentInstant)(implicit
  val ec: ExecutionContext
) extends CacheRepository[UpscanUploadWrapper](
      mongoComponent = mongo,
      collectionName = "upscan",
      domainFormat = UpscanUploadWrapper.format,
      cacheTtlIndexName = "upscan-cache-ttl",
      objName = "upscan",
      cacheTtl = config.get[FiniteDuration]("mongodb.upscan.expiry-time")
    )
    with UpscanRepository {

  override def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    EitherT(preservingMdc {
      val selector = Filters.equal("_id", upscanUpload.uploadReference.value)
      val modifier = Updates.combine(
        Updates.set("upscan", Codecs.toBson(upscanUpload)),
        Updates.set("lastUpdated", clock.currentInstant()),
        Updates.setOnInsert("_id", upscanUpload.uploadReference.value)
      )
      val options  = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      collection
        .findOneAndUpdate(selector, modifier, options)
        .toFuture()
        .map(_ => Right(()))
        .recover { case NonFatal(e) => Left(Error(e)) }

    })

  override def select(
    uploadReference: UploadReference
  ): EitherT[Future, Error, Option[UpscanUploadWrapper]] =
    EitherT(find[UpscanUploadWrapper](uploadReference.value))

  override def update(
    uploadReference: UploadReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    EitherT(preservingMdc {
      val selector = Filters.equal("_id", uploadReference.value)
      val modifier = Updates.combine(
        Updates.set("upscan", Codecs.toBson(upscanUpload)),
        Updates.set("lastUpdated", upscanUpload.uploadedOn),
        Updates.setOnInsert("_id", upscanUpload.uploadReference.value)
      )
      val options  = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      collection
        .findOneAndUpdate(selector, modifier, options)
        .toFuture()
        .map(_ => Right(()))
        .recover { case NonFatal(e) => Left(Error(e)) }
    })

  override def selectAll(
    uploadReference: List[UploadReference]
  ): EitherT[Future, Error, List[UpscanUploadWrapper]] =
    EitherT(
      preservingMdc {
        collection
          .find(in("_id", uploadReference.map(_.value): _*))
          .toFuture()
          .map { a =>
            if (a.isEmpty) {
              Left(Error(s"Could not get ids value"))
            } else {
              Right(a.toList)
            }
          }
          .recover { case exception =>
            Left(Error(exception))
          }
      }
    )
}
