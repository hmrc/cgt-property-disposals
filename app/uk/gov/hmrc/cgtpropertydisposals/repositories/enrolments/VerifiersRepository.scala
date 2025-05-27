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

package uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Indexes.ascending
import org.mongodb.scala.model.{IndexModel, IndexOptions}
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc
import org.mongodb.scala.gridfs.ObservableFuture
import org.mongodb.scala.gridfs.SingleObservableFuture

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultVerifiersRepository])
trait VerifiersRepository {
  def get(ggCredId: String): EitherT[Future, Error, Option[UpdateVerifiersRequest]]

  def insert(updateVerifiersRequest: UpdateVerifiersRequest): EitherT[Future, Error, Unit]

  def delete(ggCredId: String): EitherT[Future, Error, Int]
}

@Singleton
class DefaultVerifiersRepository @Inject() (mongo: MongoComponent)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[UpdateVerifiersRequest](
      mongoComponent = mongo,
      collectionName = "update-verifiers-requests",
      domainFormat = UpdateVerifiersRequest.format,
      indexes = Seq(IndexModel(ascending("ggCredId"), IndexOptions().name("ggCredIdIndex")))
    )
    with VerifiersRepository {

  override def get(ggCredId: String): EitherT[Future, Error, Option[UpdateVerifiersRequest]] =
    EitherT[Future, Error, Option[UpdateVerifiersRequest]](
      preservingMdc {
        collection
          .find(equal("ggCredId", ggCredId))
          .toFuture()
          .map(maybeVerifiersRequest => Right(maybeVerifiersRequest.headOption))
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

  override def insert(updateVerifiersRequest: UpdateVerifiersRequest): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      preservingMdc {
        collection
          .insertOne(updateVerifiersRequest)
          .toFuture()
          .map[Either[Error, Unit]] { result =>
            if result.wasAcknowledged() then {
              Right(())
            } else {
              Left(
                Error(
                  s"Could not insert update verifier request into database: got write errors :$result"
                )
              )
            }
          }
          .recover { case exception =>
            Left(Error(exception))
          }
      }
    )

  override def delete(ggCredId: String): EitherT[Future, Error, Int] =
    EitherT[Future, Error, Int](
      preservingMdc {
        collection
          .deleteOne(equal("ggCredId", ggCredId))
          .toFuture()
          .map(result => Right(result.getDeletedCount.intValue()))
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )
}
