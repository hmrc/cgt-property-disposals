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

import play.api.libs.json._
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.dateTimeWrite

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

trait CacheRepository[A] {
  this: ReactiveRepository[A, BSONObjectID] =>

  implicit val ec: ExecutionContext

  val cacheTtl: FiniteDuration
  val indexName: String
  val objName: String

  private lazy val index = Index(
    key     = Seq("lastUpdated" → IndexType.Ascending),
    name    = Some(indexName),
    options = BSONDocument("expireAfterSeconds" -> cacheTtl.toSeconds)
  )

  dropInvalidIndexes
    .flatMap { _ =>
      collection.indexesManager.ensure(index)
    }
    .onComplete {
      case Success(_) => logger.info("Successfully ensured indices")
      case Failure(e) => logger.warn("Could not ensure indices", e)
    }

  def set(key: String, value: A): Future[Either[Error, Unit]] =
    withCurrentTime { time =>
      val selector = Json.obj("_id"  -> key)
      val modifier = Json.obj("$set" -> Json.obj(objName -> Json.toJson(value), "lastUpdated" -> time))

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

  private def dropInvalidIndexes: Future[Unit] =
    collection.indexesManager.list().flatMap { indexes =>
      indexes
        .find { index =>
          index.name.contains(indexName) &&
          !index.options.getAs[Long]("expireAfterSeconds").contains(cacheTtl.toSeconds)
        }
        .map { i =>
          logger.warn(s"dropping $i as ttl value is incorrect for index")
          collection.indexesManager.drop(indexName).map(_ => ())
        }
        .getOrElse(Future.successful(()))
    }

}
