/*
 * Copyright 2022 HM Revenue & Customs
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
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.logger
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[DefaultAmendReturnsRepository])
trait AmendReturnsRepository {

  def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[SubmitReturnRequest]]
  def save(submitReturnRequest: SubmitReturnRequest): EitherT[Future, Error, Unit]

}

@Singleton
class DefaultAmendReturnsRepository @Inject() (mongo: MongoComponent, config: Configuration)(implicit
  val ec: ExecutionContext
) extends PlayMongoRepository[SubmitReturnRequest](
      mongoComponent = mongo,
      collectionName = "amend-returns",
      domainFormat = SubmitReturnRequest.format,
      indexes = Seq()
    )
    with AmendReturnsRepository
    with CacheRepository[SubmitReturnRequest] {

  val cacheTtl: FiniteDuration  = config.underlying.get[FiniteDuration]("mongodb.amend-returns.expiry-time").value
  val maxAmendReturns: Int      = Integer.MAX_VALUE
  val cacheTtlIndexName: String = "amend-return-cache-ttl"
  val objName: String           = "return"
  val key: String               = "return.subscribedDetails.cgtReference.value"

  override def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[SubmitReturnRequest]] =
    EitherT(
      preservingMdc {
        get(cgtReference)
      }
    )

  override def save(
    submitReturnRequest: SubmitReturnRequest
  ): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        set(submitReturnRequest.id.toString, submitReturnRequest)
      }
    )

  private def get(cgtReference: CgtReference): Future[Either[Error, List[SubmitReturnRequest]]] =
    collection
      .find(equal(key, cgtReference.value))
      .toFuture()
      .map[Either[Error, List[SubmitReturnRequest]]] { result =>
        Right(result.toList)
      }
      .recover { case NonFatal(e) =>
        logger.warn(s"Not returning draft returns: ${e.getMessage}")
        Left(Error(e))
      }

}
