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

package uk.gov.hmrc.cgtpropertydisposals.repositories.upscan

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
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
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference
  ): EitherT[Future, Error, Option[UpscanUpload]]

  def update(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit]

}

@Singleton
class DefaultUpscanRepository @Inject() (mongo: ReactiveMongoComponent, config: Configuration)(
  implicit val ec: ExecutionContext
) extends ReactiveRepository[UpscanUpload, BSONObjectID](
      collectionName = "upscan",
      mongo          = mongo.mongoConnector.db,
      UpscanUpload.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with UpscanRepository
    with CacheRepository[UpscanUpload] {

  val cacheTtl: FiniteDuration = config.underlying.get[FiniteDuration]("mongodb.upscan.expiry-time").value

  override def indexes: Seq[Index] = Seq(
    Index(
      key = Seq("draftReturnId" → IndexType.Ascending, "upscanUploadMeta.reference" -> IndexType.Ascending)
//      name     = Some("upscan-cache-ttl"),
//      unique   = true,
//      dropDups = true
    )
  )

  override def insert(
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      collection.insert
        .one[UpscanUpload](upscanUpload)
        .map[Either[Error, Unit]] { result: WriteResult =>
          if (result.ok)
            Right(())
          else
            Left(
              Error(
                s"could not store upscan upload meta :${result.writeErrors}"
              )
            )
        }
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def select(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference
  ): EitherT[Future, Error, Option[UpscanUpload]] = {
    val selector = Json.obj(
      "draftReturnId.value"        -> draftReturnId.value,
      "upscanUploadMeta.reference" -> upscanReference.value
    )
    EitherT[Future, Error, Option[UpscanUpload]](
      collection
        .find(selector, None)
        .one[UpscanUpload]
        .map(Right(_))
        .recover {
          case exception => Left(Error(exception))
        }
    )
  }

  override def update(
    draftReturnId: DraftReturnId,
    upscanReference: UpscanReference,
    upscanUpload: UpscanUpload
  ): EitherT[Future, Error, Unit] = {

    val selector = Json.obj(
      "draftReturnId.value"        -> draftReturnId.value,
      "upscanUploadMeta.reference" -> upscanReference.value
    )

    val updatedUpscanUpload = Json.toJsObject(upscanUpload)

    EitherT[Future, Error, Unit](
      findAndUpdate(
        selector,
        updatedUpscanUpload,
        fetchNewObject = false,
        upsert         = false
      ).map {
        _.lastError.flatMap(_.err) match {
          case Some(error) => Left(Error(s"could not update upscan upload: $error"))
          case None        => Right(())
        }
      }
    )
  }

  override val indexName: String = "upscan-cache-ttl"
  override val objName: String   = ""
}
