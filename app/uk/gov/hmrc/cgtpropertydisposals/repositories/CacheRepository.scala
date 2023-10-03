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

package uk.gov.hmrc.cgtpropertydisposals.repositories

import com.mongodb.client.model.Indexes.ascending
import org.mongodb.scala.MongoCollection
import org.mongodb.scala.model.Filters.{equal, in}
import org.mongodb.scala.model._
import org.slf4j.Logger
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DefaultDraftReturnsRepository.DraftReturnWithCgtReferenceWrapper
import uk.gov.hmrc.mongo.play.json.Codecs.logger
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.util.{Failure, Success}
import scala.util.chaining.scalaUtilChainingOps
import scala.util.control.NonFatal

trait CacheRepository[A] extends PlayMongoRepository[A] {

  implicit val ec: ExecutionContext

  val cacheTtl: FiniteDuration
  val cacheTtlIndexName: String
  val objName: String

  private lazy val cacheTtlIndex = IndexModel(
    ascending("lastUpdated"),
    IndexOptions().name(cacheTtlIndexName).expireAfter(cacheTtl.toSeconds, TimeUnit.SECONDS)
  )

  private val now = LocalDateTime.now()

  private def withCurrentTime[B](f: LocalDateTime => B) = f(now)

  def ensureIndexes(implicit ec: ExecutionContext): Future[Seq[String]] =
    for {
      result <- super.ensureIndexes()
      _      <- CacheRepository.setTtlIndex(cacheTtlIndex, cacheTtlIndexName, cacheTtl, collection, logger)
    } yield result

  def set(
    id: String,
    value: DraftReturnWithCgtReferenceWrapper,
    overrideLastUpdatedTime: Option[LocalDateTime] = None
  ): Future[Either[Error, Unit]] =
    preservingMdc {
      withCurrentTime { time =>
        val lastUpdated: LocalDateTime = overrideLastUpdatedTime.map(toJavaDateTime).getOrElse(time)
        val selector                   = equal("_id", id)
        val modifier                   =
          Updates.combine(Updates.set(objName, Codecs.toBson(value.`return`)), Updates.set("lastUpdated", lastUpdated))

        collection
          .findOneAndUpdate(
            filter = selector,
            update = modifier,
            options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER).upsert(true)
          )
          .toFuture()
          .map { result =>
            if (result == result)
              Right(())
            else
              Left(Error(s"Could not store draft return: $result"))
          }
          .recover { case NonFatal(e) =>
            Left(Error(e))
          }
      }
    }

  def findAll[C : ClassTag](ids: List[String]): Future[Either[Error, List[C]]] =
    preservingMdc {
      collection
        .find[C](in("_id", ids))
        .headOption()
        .map {
          case None       => Left(Error(s"Could not get id value"))
          case Some(list) => Right(List(list))
        }
        .recover { case exception =>
          Left(Error(exception))
        }
    }

  def find[C : ClassTag](id: String): Future[Either[Error, Option[C]]] =
    preservingMdc {
      collection
        .find[C](equal("_id", id))
        .headOption()
        .map {
          case None       => Left(Error(s"Could not find json for id $id"))
          case Some(json) => Right(Some(json))
        }
        .recover { case exception =>
          Left(Error(exception))
        }
    }

  private def toJavaDateTime(dateTime: LocalDateTime) =
    LocalDateTime.of(
      dateTime.getYear,
      dateTime.getMonthValue,
      dateTime.getDayOfMonth,
      dateTime.getHour,
      dateTime.getMinute,
      dateTime.getSecond
    )
}

object CacheRepository {
  def setTtlIndex[A](
    ttlIndex: IndexModel,
    ttlIndexName: String,
    ttl: Duration,
    collection: MongoCollection[A],
    logger: Logger
  )(implicit ex: ExecutionContext): Future[String] =
    (for {
      indexes   <- collection.listIndexes().toFuture()
      maybeIndex = indexes.find(index => index.contains(ttlIndexName) && !index.containsValue(ttl))
      _         <- maybeIndex match {
                     case Some(i) =>
                       logger.warn(s"dropping $i as ttl value is incorrect for index")
                       collection.dropIndex(ttlIndexName).toFuture().map(_ => ())
                     case None    => Future.successful(())
                   }
      result    <- collection.createIndex(ttlIndex.getKeys).toFuture()
    } yield result).transform(_.tap {
      case Success(e) => logger.warn("Could not ensure ttl index", e)
      case Failure(_) => logger.info("Successfully ensured ttl index")
    })
}
