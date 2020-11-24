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

package uk.gov.hmrc.cgtpropertydisposals.repositories

import java.time.LocalDateTime

import cats.data.EitherT
import cats.instances.list._
import cats.syntax.either._
import org.joda.time.DateTime
import org.slf4j.Logger
import play.api.libs.json._
import reactivemongo.api.Cursor
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import reactivemongo.play.json.collection.JSONCollection
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ListUtils._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.dateTimeWrite
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait CacheRepository[A] extends ReactiveRepository[A, BSONObjectID] {

  implicit val ec: ExecutionContext

  val cacheTtl: FiniteDuration
  val cacheTtlIndexName: String
  val objName: String

  private lazy val cacheTtlIndex = Index(
    key = Seq("lastUpdated" → IndexType.Ascending),
    name = Some(cacheTtlIndexName),
    options = BSONDocument("expireAfterSeconds" -> cacheTtl.toSeconds)
  )

  override def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[Boolean]] =
    for {
      result <- super.ensureIndexes(ExecutionContext.global)
      _      <- CacheRepository.setTtlIndex(cacheTtlIndex, cacheTtlIndexName, cacheTtl, collection, logger)(
                  ExecutionContext.global
                )
    } yield result

  def set(id: String, value: A, overrideLastUpdatedTime: Option[LocalDateTime] = None): Future[Either[Error, Unit]] =
    preservingMdc {
      withCurrentTime { time =>
        val lastUpdated: DateTime = overrideLastUpdatedTime.map(toJodaDateTime).getOrElse(time)
        val selector              = Json.obj("_id" -> id)
        val modifier              = Json.obj(
          "$set" -> Json
            .obj(
              objName       -> Json.toJson(value),
              "lastUpdated" -> lastUpdated
            )
        )

        collection
          .update(false)
          .one(selector, modifier, upsert = true)
          .map { writeResult =>
            if (writeResult.ok)
              Right(())
            else
              Left(Error(s"Could not store draft return: ${writeResult.errmsg.getOrElse("-")}"))
          }
          .recover { case NonFatal(e) =>
            Left(Error(e))
          }
      }
    }

  def findAll(ids: List[String]): Future[Either[Error, List[A]]] =
    preservingMdc {
      collection
        .find(
          Json.obj("_id" -> Json.obj("$in" -> ids)),
          None
        )
        .cursor[JsObject]()
        .collect[List](Int.MaxValue, Cursor.FailOnError[List[JsObject]]())
        .map { list =>
          val (errors, values) = EitherT(list.map(readJson))
            .subflatMap(
              Either.fromOption(_, "Could not find value in JSON")
            )
            .value
            .partitionWith(identity)

          if (errors.nonEmpty)
            Left(Error(s"Could not get all values: ${errors.mkString(", ")}]"))
          else
            Right(values)
        }
        .recover { case exception =>
          Left(Error(exception))
        }
    }

  def find(id: String): Future[Either[Error, Option[A]]] =
    preservingMdc {
      collection
        .find(
          Json.obj("_id" -> id),
          None
        )
        .one[JsObject]
        .map {
          case None       => Left(Error(s"Could not find json for id $id"))
          case Some(json) => readJson(json).leftMap(Error(_))
        }
        .recover { case exception =>
          Left(Error(exception))
        }
    }

  private def readJson(jsObject: JsObject): Either[String, Option[A]] =
    (jsObject \ objName)
      .validateOpt[A]
      .asEither
      .leftMap(e ⇒ s"Could not parse session data from mongo: ${e.mkString("; ")}")

  private def toJodaDateTime(dateTime: LocalDateTime): org.joda.time.DateTime =
    new org.joda.time.DateTime(
      dateTime.getYear,
      dateTime.getMonthValue,
      dateTime.getDayOfMonth,
      dateTime.getHour,
      dateTime.getMinute,
      dateTime.getSecond
    )

}

object CacheRepository {

  def setTtlIndex(
    ttlIndex: Index,
    ttlIndexName: String,
    ttl: FiniteDuration,
    collection: JSONCollection,
    logger: Logger
  )(implicit ex: ExecutionContext): Future[WriteResult] = {
    def dropInvalidIndexes(): Future[Unit] =
      collection.indexesManager.list().flatMap { indexes =>
        indexes
          .find { index =>
            index.name.contains(ttlIndexName) &&
            !index.options.getAs[Long]("expireAfterSeconds").contains(ttl.toSeconds)
          }
          .map { i =>
            logger.warn(s"dropping $i as ttl value is incorrect for index")
            collection.indexesManager.drop(ttlIndexName).map(_ => ())
          }
          .getOrElse(Future.successful(()))
      }

    dropInvalidIndexes()
      .flatMap(_ => collection.indexesManager.create(ttlIndex))
      .transform { result =>
        result.fold(
          e => logger.warn("Could not ensure ttl index", e),
          _ => logger.info("Successfully ensured ttl index")
        )
        result
      }
  }

}
