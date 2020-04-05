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

import cats.syntax.either._
import org.joda.time.DateTime
import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.dateTimeWrite

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait CacheRepository[A] {
  this: ReactiveRepository[A, BSONObjectID] =>

  implicit val ec: ExecutionContext

  val cacheTtl: FiniteDuration
  val cacheTtlIndexName: String
  val objName: String

  private lazy val cacheTtlIndex = Index(
    key     = Seq("lastUpdated" → IndexType.Ascending),
    name    = Some(cacheTtlIndexName),
    options = BSONDocument("expireAfterSeconds" -> cacheTtl.toSeconds)
  )

  dropInvalidIndexes()
    .flatMap(_ => collection.indexesManager.create(cacheTtlIndex))
    .onComplete {
      case Success(_) => logger.info("Successfully created ttl and id indices")
      case Failure(e) => logger.warn("Could not create ttl and id indices", e)
    }

  def set(id: String, value: A, overrideLastUpdatedTime: Option[LocalDateTime] = None): Future[Either[Error, Unit]] =
    withCurrentTime { time =>
      val lastUpdated: DateTime = overrideLastUpdatedTime.map(toJodaDateTime).getOrElse(time)
      val selector              = Json.obj("_id" -> id)
      val modifier = Json.obj(
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
          if (writeResult.ok) {
            Right(())
          } else {
            Left(Error(s"Could not store draft return: ${writeResult.errmsg.getOrElse("-")}"))
          }
        }
        .recover {
          case NonFatal(e) => Left(Error(e))
        }
    }

  def find(id: String): Future[Either[Error, Option[A]]] =
    collection
      .find(
        Json.obj("_id" -> id),
        None
      )
      .one[JsObject]
      .map {
        case None =>
          Left(Error(s"Could not find json for id $id"))
        case Some(json) =>
          (json \ objName)
            .validateOpt[A]
            .asEither
            .leftMap(e ⇒ Error(s"Could not parse session data from mongo: ${e.mkString("; ")}"))
      }
      .recover {
        case exception => Left(Error(exception))
      }

  private def dropInvalidIndexes(): Future[Unit] =
    collection.indexesManager.list().flatMap { indexes =>
      indexes
        .find { index =>
          index.name.contains(cacheTtlIndexName) &&
          !index.options.getAs[Long]("expireAfterSeconds").contains(cacheTtl.toSeconds)
        }
        .map { i =>
          logger.warn(s"dropping $i as ttl value is incorrect for index")
          collection.indexesManager.drop(cacheTtlIndexName).map(_ => ())
        }
        .getOrElse(Future.successful(()))
    }

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
