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

package uk.gov.hmrc.cgtpropertydisposals.repositories.returns

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.Cursor
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ListUtils._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.cgtpropertydisposals.util.JsErrorOps._
import uk.gov.hmrc.mongo.ReactiveRepository
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
class DefaultAmendReturnsRepository @Inject() (component: ReactiveMongoComponent, config: Configuration)(implicit
  val ec: ExecutionContext
) extends ReactiveRepository[SubmitReturnRequest, BSONObjectID](
      collectionName = "amend-returns",
      mongo = component.mongoConnector.db,
      domainFormat = SubmitReturnRequest.format
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

  private def get(cgtReference: CgtReference): Future[Either[Error, List[SubmitReturnRequest]]] = {
    val selector = Json.obj(key -> cgtReference.value)
    collection
      .find(selector, None)
      .cursor[JsValue]()
      .collect[List](maxAmendReturns, Cursor.FailOnError[List[JsValue]]())
      .map { l =>
        val p: List[Either[Error, SubmitReturnRequest]] = l.map { json =>
          (json \ objName).validate[SubmitReturnRequest].asEither.leftMap(toError)
        }
        val (errors, draftReturns)                      = p.partitionWith(identity)
        if (errors.nonEmpty)
          logger.warn(s"Not returning ${errors.size} draft returns: ${errors.mkString("; ")}")

        Right(draftReturns)
      }
      .recover { case NonFatal(e) =>
        Left(Error(e))
      }
  }

  private def toError(e: Seq[(JsPath, Seq[JsonValidationError])]): Error =
    Error(JsError(e).prettyPrint())

}
