/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.repositories

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifierDetails
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultVerifiersRepository])
trait VerifiersRepository {
  def get(ggCredId: String): EitherT[Future, Error, Option[UpdateVerifierDetails]]
  def insert(updateVerifiersRequest: UpdateVerifierDetails): EitherT[Future, Error, Unit]
  def delete(ggCredId: String): EitherT[Future, Error, Int]
}

class DefaultVerifiersRepository @Inject()(mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[UpdateVerifierDetails, BSONObjectID](
      collectionName = "update-verifiers-requests",
      mongo          = mongo.mongoConnector.db,
      UpdateVerifierDetails.updateVerifiersFormat,
      ReactiveMongoFormats.objectIdFormats
    )
    with VerifiersRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("ggCredId" → IndexType.Ascending),
      name = Some("ggCredIdIndex")
    )
  )

  override def get(ggCredId: String): EitherT[Future, Error, Option[UpdateVerifierDetails]] =
    EitherT[Future, Error, Option[UpdateVerifierDetails]](
      collection
        .find(Json.obj("ggCredId" -> ggCredId), None)
        .one[UpdateVerifierDetails]
        .map { maybeVerifiersRequest =>
          Right(maybeVerifiersRequest)
        }
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )

  override def insert(updateVerifiersRequest: UpdateVerifierDetails): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      collection.insert
        .one[UpdateVerifierDetails](updateVerifiersRequest)
        .map[Either[Error, Unit]] { result: WriteResult =>
          if (result.ok)
            Right(())
          else
            Left(
              Error(
                s"Could not insert update verifier request into database: got write errors :${result.writeErrors}"
              )
            )
        }
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def delete(ggCredId: String): EitherT[Future, Error, Int] =
    EitherT[Future, Error, Int](
      collection.delete
        .one(Json.obj("ggCredId" -> ggCredId))
        .map { result: WriteResult =>
          Right(result.n)
        }
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )

}
