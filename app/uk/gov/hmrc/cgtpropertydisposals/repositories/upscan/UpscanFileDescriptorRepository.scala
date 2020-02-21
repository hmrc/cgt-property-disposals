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
import play.api.libs.json.{JsObject, JsString, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadConcern
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{FileDescriptorId, UpscanFileDescriptor}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import play.api.libs.json._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultUpscanFileDescriptorRepository])
trait UpscanFileDescriptorRepository {
  def insert(upscanFileDescriptor: UpscanFileDescriptor): EitherT[Future, Error, Unit]
  def count(cgtReference: CgtReference): EitherT[Future, Error, Int]
  def get(
    fileDescriptorId: FileDescriptorId
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]]

  def getAll(cgtReference: CgtReference): EitherT[Future, Error, List[UpscanFileDescriptor]]
  def updateUpscanUploadStatus(upscanFileDescriptor: UpscanFileDescriptor): EitherT[Future, Error, Boolean]
}

@Singleton
class DefaultUpscanFileDescriptorRepository @Inject() (mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[UpscanFileDescriptor, BSONObjectID](
      collectionName = "upscan-file-descriptor",
      mongo          = mongo.mongoConnector.db,
      UpscanFileDescriptor.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with UpscanFileDescriptorRepository {

  private val defaultReadConcern: ReadConcern = mongo.mongoConnector.helper.connectionOptions.readConcern

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("cgtReference" → IndexType.Ascending),
      name = Some("cgt-reference")
    )
  )

  override def updateUpscanUploadStatus(
    upscanFileDescriptor: UpscanFileDescriptor
  ): EitherT[Future, Error, Boolean] = {
    val selector = Json.obj("key"  -> upscanFileDescriptor.key)
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

  override def count(cgtReference: CgtReference): EitherT[Future, Error, Int] = {
    val query = Json.obj("cgtReference" -> cgtReference)
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
    cgtReference: CgtReference
  ): EitherT[Future, Error, List[UpscanFileDescriptor]] =
    EitherT[Future, Error, List[UpscanFileDescriptor]](
      find("cgtReference" -> Json.obj("value" -> JsString(cgtReference.value)))
        .map(Right(_))
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def get(
    fileDescriptorId: FileDescriptorId
  ): EitherT[Future, Error, Option[UpscanFileDescriptor]] =
    EitherT[Future, Error, Option[UpscanFileDescriptor]](
      find("key" -> fileDescriptorId.value)
        .map(fd => Right(fd.headOption))
        .recover {
          case exception => Left(Error(exception))
        }
    )

}
