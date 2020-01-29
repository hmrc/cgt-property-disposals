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

package uk.gov.hmrc.cgtpropertydisposals.repositories.returns

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}

import scala.concurrent.{ExecutionContext, Future}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

@ImplementedBy(classOf[DefaultDraftReturnsRepository])
trait DraftReturnsRepository {

  def save(draftReturn: DraftReturn): EitherT[Future, Error, Unit]

}

@Singleton
class DefaultDraftReturnsRepository @Inject() (mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[DraftReturn, BSONObjectID](
      collectionName = "draft-returns",
      mongo          = mongo.mongoConnector.db,
      DraftReturn.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with DraftReturnsRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("id" → IndexType.Ascending),
      name = Some("idIndex")
    )
  )

  override def save(draftReturn: DraftReturn): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      insert(draftReturn)
        .map[Either[Error, Unit]] { result: WriteResult =>
          if (result.ok)
            Right(())
          else
            Left(
              Error(
                s"Could not insert draft return into database: got write errors :${result.writeErrors}"
              )
            )
        }
        .recover {
          case exception => Left(Error(exception))
        }
    )
}
