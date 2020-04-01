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
import play.api.libs.json.{JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadConcern
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanCallBack, UpscanFileDescriptor, UpscanInitiateReference}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultUpscanFileDescriptorRepository])
trait UpscanFileDescriptorRepository {
  def insert(upscanFileDescriptor: UpscanFileDescriptor): EitherT[Future, Error, Unit]
  def count(draftReturnId: DraftReturnId): EitherT[Future, Error, Int]
  def get(
    draftReturnId: DraftReturnId,
    upscanInitiateReference: UpscanInitiateReference
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]]

  def getAll(draftReturnId: DraftReturnId): EitherT[Future, Error, List[UpscanFileDescriptor]]
  def updateUpscanUploadStatus(upscanFileDescriptor: UpscanFileDescriptor): EitherT[Future, Error, Boolean]
  def updateStatus(upscanCallBack: UpscanCallBack): EitherT[Future, Error, Boolean]
  def deleteFile(
    draftReturnId: DraftReturnId,
    upscanInitiateReference: UpscanInitiateReference
  ): EitherT[Future, Error, Unit]
  def deleteAllFiles(
    draftReturnId: DraftReturnId
  ): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultUpscanFileDescriptorRepository @Inject() (mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[UpscanFileDescriptor, BSONObjectID](
      collectionName = "upscan",
      mongo          = mongo.mongoConnector.db,
      UpscanFileDescriptor.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with UpscanFileDescriptorRepository {

  private val defaultReadConcern: ReadConcern = mongo.mongoConnector.helper.connectionOptions.readConcern

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("draftReturnId" → IndexType.Ascending),
      name = Some("draft-return-id")
    )
  )

  override def updateStatus(upscanCallBack: UpscanCallBack): EitherT[Future, Error, Boolean] = {
    val selector = Json.obj(
      "upscanInitiateReference" -> UpscanInitiateReference(upscanCallBack.reference),
      "draftReturnId"           -> upscanCallBack.draftReturnId
    )
    val update = Json.obj("$set" -> Json.obj("status" -> upscanCallBack.fileStatus))

    EitherT[Future, Error, Boolean](
      findAndUpdate(
        selector,
        update,
        fetchNewObject = false,
        upsert         = false
      ).map {
        _.lastError.flatMap(_.err) match {
          case Some(_) => Right(false)
          case None    => Right(true)
        }
      }
    )
  }

  override def updateUpscanUploadStatus(
    upscanFileDescriptor: UpscanFileDescriptor
  ): EitherT[Future, Error, Boolean] = {
    val selector = Json.obj("key"  -> upscanFileDescriptor.upscanInitiateReference)
    val update   = Json.obj("$set" -> Json.obj("status" -> upscanFileDescriptor.status))

    EitherT[Future, Error, Boolean](
      findAndUpdate(
        selector,
        update,
        fetchNewObject = false,
        upsert         = false
      ).map {
        _.lastError.flatMap(_.err) match {
          case Some(_) => Right(false)
          case None    => Right(true)
        }
      }
    )
  }

  override def insert(
    upscanFileDescriptor: UpscanFileDescriptor
  ): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      collection.insert
        .one[UpscanFileDescriptor](upscanFileDescriptor)
        .map[Either[Error, Unit]] { result: WriteResult =>
          if (result.ok)
            Right(())
          else
            Left(
              Error(
                s"Could not insert upscan file descriptor request into database: got write errors :${result.writeErrors}"
              )
            )
        }
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def count(draftReturnId: DraftReturnId): EitherT[Future, Error, Int] = {
    val query = Json.obj("draftReturnId" -> draftReturnId)
    EitherT[Future, Error, Int](
      collection
        .count(Some(query), limit = None, skip = 0, hint = None, readConcern = defaultReadConcern)
        .map[Either[Error, Int]] { c =>
          Right(c.toInt)
        }
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )
  }

  override def getAll(
    draftReturnId: DraftReturnId
  ): EitherT[Future, Error, List[UpscanFileDescriptor]] =
    EitherT[Future, Error, List[UpscanFileDescriptor]](
      find("draftReturnId" -> Json.obj("value" -> JsString(draftReturnId.value)))
        .map(Right(_))
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def get(
    draftReturnId: DraftReturnId,
    upscanInitiateReference: UpscanInitiateReference
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]] = {
    val selector = Json.obj("upscanInitiateReference" -> upscanInitiateReference, "draftReturnId" -> draftReturnId)
    EitherT[Future, Error, Option[UpscanFileDescriptor]](
      collection
        .find(selector, None)
        .one[UpscanFileDescriptor]
        .map(Right(_))
        .recover {
          case exception => Left(Error(exception))
        }
    )
  }

  override def deleteFile(
    draftReturnId: DraftReturnId,
    upscanInitiateReference: UpscanInitiateReference
  ): EitherT[Future, Error, Unit] = {
    val selector = Json.obj("upscanInitiateReference" -> upscanInitiateReference, "draftReturnId" -> draftReturnId)
    EitherT[Future, Error, Unit](
      collection
        .delete()
        .one(selector, Some(1))
        .map(_ => Right(()))
        .recover {
          case exception => Left(Error(exception))
        }
    )
  }

  override def deleteAllFiles(
    draftReturnId: DraftReturnId
  ): EitherT[Future, Error, Unit] = {
    val selector = Json.obj("draftReturnId" -> draftReturnId)
    EitherT[Future, Error, Unit](
      collection
        .delete()
        .one(selector, None)
        .map(_ => Right(()))
        .recover {
          case exception => Left(Error(exception))
        }
    )
  }

}
