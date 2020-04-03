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

class UpscanCallBackRepositoryImpl {}

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.ReadConcern
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultUpscanCallBackRepository])
trait UpscanCallBackRepository {
  def insert(upscanCallBack: UpscanCallBack): EitherT[Future, Error, Unit]
  def count(draftReturnId: DraftReturnId): EitherT[Future, Error, Long]
  def getAll(draftReturnId: DraftReturnId): EitherT[Future, Error, List[UpscanCallBack]]
}

@Singleton
class DefaultUpscanCallBackRepository @Inject() (mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[UpscanCallBack, BSONObjectID](
      collectionName = "upscan-call-back",
      mongo          = mongo.mongoConnector.db,
      UpscanCallBack.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with UpscanCallBackRepository {

  private val defaultReadConcern: ReadConcern = mongo.mongoConnector.helper.connectionOptions.readConcern

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("draftReturnId" → IndexType.Ascending),
      name = Some("draft-return-id")
    )
  )

  override def insert(upscanCallBack: UpscanCallBack): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      collection.insert
        .one[UpscanCallBack](upscanCallBack)
        .map[Either[Error, Unit]] { result: WriteResult =>
          if (result.ok)
            Right(())
          else
            Left(
              Error(
                s"Could not insert upscan call back event into database: got write errors :${result.writeErrors}"
              )
            )
        }
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def count(draftReturnId: DraftReturnId): EitherT[Future, Error, Long] = {
    val query = Json.obj("draftReturnId" -> Some(draftReturnId))
    EitherT[Future, Error, Long](
      collection
        .count(Some(query), limit = None, skip = 0, hint = None, readConcern = defaultReadConcern)
        .map[Either[Error, Long]](c => Right(c))
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )
  }

  override def getAll(draftReturnId: DraftReturnId): EitherT[Future, Error, List[UpscanCallBack]] =
    EitherT[Future, Error, List[UpscanCallBack]](
      find("draftReturnId" -> Some(draftReturnId))
        .map[Either[Error, List[UpscanCallBack]]](c => Right(c))
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )

}
