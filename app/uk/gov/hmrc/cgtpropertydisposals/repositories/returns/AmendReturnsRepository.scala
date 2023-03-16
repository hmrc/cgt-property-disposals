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

package uk.gov.hmrc.cgtpropertydisposals.repositories.returns

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.{Filters, FindOneAndUpdateOptions, ReturnDocument, Updates}
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnWrapper}
import uk.gov.hmrc.cgtpropertydisposals.repositories.{CacheRepository, CurrentInstant}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.logger
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[DefaultAmendReturnsRepository])
trait AmendReturnsRepository {

  def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[SubmitReturnWrapper]]
  def save(
    submitReturnRequest: SubmitReturnRequest
  ): EitherT[Future, Error, Unit]

}

@Singleton
class DefaultAmendReturnsRepository @Inject() (mongo: MongoComponent, config: Configuration, clock: CurrentInstant)(
  implicit val ec: ExecutionContext
) extends PlayMongoRepository[SubmitReturnWrapper](
      mongoComponent = mongo,
      collectionName = "amend-returns",
      domainFormat = SubmitReturnWrapper.format,
      indexes = Seq()
    )
    with AmendReturnsRepository
    with CacheRepository[SubmitReturnWrapper] {

  val cacheTtl: FiniteDuration  = config.underlying.get[FiniteDuration]("mongodb.amend-returns.expiry-time").value
  val maxAmendReturns: Int      = Integer.MAX_VALUE
  val cacheTtlIndexName: String = "amend-return-cache-ttl"
  val objName: String           = "return"
  val key: String               = "return.subscribedDetails.cgtReference.value"

  override def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[SubmitReturnWrapper]] =
    EitherT(
      preservingMdc {
        get(cgtReference)
      }
    )

  override def save(
    submitReturnRequest: SubmitReturnRequest
  ): EitherT[Future, Error, Unit] =
    EitherT(preservingMdc {
      val selector = Filters.equal("_id", submitReturnRequest.id.toString)
      val modifier = Updates.combine(
        Updates.set("return", Codecs.toBson(submitReturnRequest)),
        Updates.set("lastUpdated", clock.currentInstant()),
        Updates.setOnInsert("_id", submitReturnRequest.id.toString)
      )
      val options  = FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER)
      collection
        .findOneAndUpdate(selector, modifier, options)
        .toFuture()
        .map(_ => Right(()))
        .recover { case NonFatal(e) => Left(Error(e)) }

    })

  private def get(cgtReference: CgtReference): Future[Either[Error, List[SubmitReturnWrapper]]] =
    collection
      .find(equal(key, cgtReference.value))
      .toFuture()
      .map[Either[Error, List[SubmitReturnWrapper]]] { result =>
        Right(result.toList)
      }
      .recover { case NonFatal(e) =>
        logger.warn(s"Not returning draft returns: ${e.getMessage}")
        Left(Error(e))
      }

}
