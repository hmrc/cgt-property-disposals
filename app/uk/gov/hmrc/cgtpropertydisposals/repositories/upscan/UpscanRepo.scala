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
import com.mongodb.client.model.Indexes.ascending
import org.mongodb.scala.model._
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.upscan._
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.mongo.{MongoComponent, TimestampSupport}
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[DefaultUpscanRepo])
trait UpscanRepo {
  def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultUpscanRepo @Inject() (mongo: MongoComponent, config: Configuration)(implicit
  val ec: ExecutionContext
) extends PlayMongoRepository[UpscanUploadWrapper](
      mongoComponent = mongo,
      collectionName = "upscan",
      domainFormat = UpscanUploadWrapper.format,
      indexes = Seq(
        IndexModel(
          ascending("lastUpdated"),
          IndexOptions()
            .name("upscan-cache-ttl")
            .expireAfter(
              config.get[FiniteDuration]("mongodb.upscan.expiry-time").toSeconds,
              TimeUnit.SECONDS
            )
        )
      )
    )
    with UpscanRepo
    with TimestampSupport {
  override def timestamp(): Instant = Instant.now()

  override def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    EitherT(preservingMdc {
      val time     = timestamp()
      val selector = Filters.equal("_id", upscanUpload.uploadReference.value)
      val modifier = Updates.combine(
        Updates.set("upscan", Codecs.toBson(upscanUpload)),
        Updates.set("lastUpdated", time),
        Updates.setOnInsert("_id", upscanUpload.uploadReference.value)
      )
      val options  = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      collection
        .findOneAndUpdate(selector, modifier, options)
        .toFuture()
        .map(_ => Right(()))
        .recover { case NonFatal(e) => Left(Error(e)) }

    })
}
