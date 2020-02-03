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

import com.google.inject.Singleton
import play.api.libs.json.{JsValue, Json}
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.collection.JSONBatchCommands.FindAndModifyCommand
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.mongo.ReactiveRepository
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import scala.concurrent.Future
import scala.util.{Failure, Success}
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats.dateTimeWrite
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.control.NonFatal

@Singleton
trait CacheRepository[A] { this: ReactiveRepository[A, BSONObjectID] =>

  val cacheTtl: Int
  val indexName: String
  val objName: String

  private val index = Index(
    key     = Seq("lastUpdated" → IndexType.Ascending),
    name    = Some(indexName),
    options = BSONDocument("expireAfterSeconds" -> cacheTtl)
  )

  dropInvalidIndexes
    .flatMap { _ =>
      collection.indexesManager.ensure(index)
    }
    .onComplete {
      case Success(_) => logger.info("Successfully ensured indices")
      case Failure(e) => logger.warn("Could not ensure indices", e)
    }

  def get(key: String): Future[Either[Error, Option[JsValue]]] =
    collection
      .find(Json.obj("_id" -> key), None)
      .one[A]
      .map(dr => Right(dr.map(Json.toJson(_))))
      .recover {
        case NonFatal(e) => Left(Error(e))
      }

  def set(key: String, value: JsValue): Future[Either[Error, Unit]] =
    withCurrentTime { time =>
      val selector = Json.obj("_id"  -> key)
      val modifier = Json.obj("$set" -> Json.obj(objName -> value, "lastUpdated" -> time))

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

  def invalidate(key: String): Future[FindAndModifyCommand.FindAndModifyResult] = {

    val selector = Json.obj("_id" -> key)

    collection.findAndRemove(selector)
  }

  private def dropInvalidIndexes: Future[Unit] =
    collection.indexesManager.list().flatMap { indexes =>
      indexes
        .find { index =>
          index.name.contains(indexName) &&
          !index.options.getAs[Int]("expireAfterSeconds").contains(cacheTtl)
        }
        .map { i =>
          logger.warn(s"dropping $i as ttl value is incorrect for index")
          collection.indexesManager.drop(indexName).map(_ => ())
        }
        .getOrElse(Future.successful(()))
    }

}
