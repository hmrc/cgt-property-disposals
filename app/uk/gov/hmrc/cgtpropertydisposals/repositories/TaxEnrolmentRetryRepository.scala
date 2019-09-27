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
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor.FailOnError
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultTaxEnrolmentRetryRepository])
trait TaxEnrolmentRetryRepository {
  def insert(cgtEnrolmentRequest: TaxEnrolmentRequest): EitherT[Future, Error, Boolean]
  def delete(userId: String): EitherT[Future, Error, Int]
  def getAllNonFailedEnrolmentRequests(): EitherT[Future, Error, List[TaxEnrolmentRequest]]
  def updateStatusToFail(
    userId: String
  ): EitherT[Future, Error, Option[TaxEnrolmentRequest]]
}

@Singleton
class DefaultTaxEnrolmentRetryRepository @Inject()(mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[TaxEnrolmentRequest, BSONObjectID](
      collectionName = "tax-enrolment-retry",
      mongo          = mongo.mongoConnector.db,
      TaxEnrolmentRequest.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with TaxEnrolmentRetryRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("userId" → IndexType.Ascending),
      name = Some("userIdIndex")
    )
  )

  override def insert(cgtEnrolmentRequest: TaxEnrolmentRequest): EitherT[Future, Error, Boolean] =
    EitherT[Future, Error, Boolean](
      collection.insert
        .one[TaxEnrolmentRequest](cgtEnrolmentRequest)
        .map { result: WriteResult =>
          Right(result.ok)
        }
        .recover {
          case exception =>
            Left(Error(exception.getMessage))
        }
    )

  override def delete(userId: String): EitherT[Future, Error, Int] =
    EitherT[Future, Error, Int](
      collection.delete
        .one(Json.obj("userId" -> userId))
        .map { result: WriteResult =>
          Right(result.n)
        }
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )

  override def getAllNonFailedEnrolmentRequests(): EitherT[Future, Error, List[TaxEnrolmentRequest]] =
    EitherT[Future, Error, List[TaxEnrolmentRequest]](
      collection
        .find(Json.obj("status" -> "Retry"), None)
        .cursor[TaxEnrolmentRequest]()
        .collect(maxDocs = -1, FailOnError[List[TaxEnrolmentRequest]]())
        .map { maybeEnrolmentRequest =>
          Right(maybeEnrolmentRequest)
        }
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )

  override def updateStatusToFail(
    userId: String
  ): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    EitherT[Future, Error, Option[TaxEnrolmentRequest]](
      collection
        .findAndUpdate(
          Json.obj("userId" -> userId),
          Json.obj("$set"   -> Json.obj("status" -> "Failed")),
          true
        )
        .map(r => Right(r.result[TaxEnrolmentRequest]))
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )
}
