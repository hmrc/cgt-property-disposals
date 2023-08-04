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
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import org.mongodb.scala.model.Filters
import org.mongodb.scala.model.Filters.{equal, or}
import play.api.Configuration
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DefaultDraftReturnsRepository.{DraftReturnWithCgtReference, DraftReturnWithCgtReferenceWrapper}
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.logger
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.time.Instant
import java.util.UUID
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[DefaultDraftReturnsRepository])
trait DraftReturnsRepository {
  def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[DraftReturn]]

  def save(
    draftReturn: DraftReturn,
    cgtReference: CgtReference
  ): EitherT[Future, Error, Unit]

  def delete(cgtReference: CgtReference): EitherT[Future, Error, Unit]

  def deleteAll(draftReturnIds: List[UUID]): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultDraftReturnsRepository @Inject() (mongo: MongoComponent, config: Configuration)(implicit
  val ec: ExecutionContext
) extends PlayMongoRepository[DraftReturnWithCgtReferenceWrapper](
      mongoComponent = mongo,
      collectionName = "draft-returns",
      domainFormat = DraftReturnWithCgtReferenceWrapper.format,
      indexes = Seq()
    )
    with DraftReturnsRepository
    with CacheRepository[DraftReturnWithCgtReferenceWrapper] {

  val cacheTtl: FiniteDuration  = config.underlying.get[FiniteDuration]("mongodb.draft-returns.expiry-time").value
  val cacheTtlIndexName: String = "draft-return-cache-ttl"
  val objName: String           = "return"
  val key: String               = "return.cgtReference.value"

  override def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[DraftReturn]] =
    EitherT(
      preservingMdc {
        get(cgtReference)
      }
    ).map(_.map(_.draftReturn))

  override def save(
    draftReturn: DraftReturn,
    cgtReference: CgtReference
  ): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        set(
          draftReturn.id.toString,
          DraftReturnWithCgtReferenceWrapper(
            draftReturn.id.toString,
            Instant.now(),
            DraftReturnWithCgtReference(draftReturn, cgtReference, draftReturn.id)
          )
        )
      }
    )

  override def delete(cgtReference: CgtReference): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      preservingMdc {
        collection
          .deleteMany(equal("return.cgtReference.value", cgtReference.value))
          .toFuture()
          .map { result =>
            if (result.wasAcknowledged())
              Right(())
            else
              Left(
                Error(
                  s"WriteResult after trying to delete did not come back ok. Got write errors [$result]"
                )
              )
          }
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

  override def deleteAll(draftReturnIds: List[UUID]): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      preservingMdc {

        collection
          .deleteMany(
            or(draftReturnIds.map(id => equal("return.draftId", Codecs.toBson(id))): _*)
          )
          .toFuture()
          .map { result =>
            if (result.wasAcknowledged())
              Right(())
            else
              Left(
                Error(
                  s"WriteResult after trying to delete did not come back ok. Got write errors [$result]"
                )
              )
          }
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

  private def get(cgtReference: CgtReference): Future[Either[Error, List[DraftReturnWithCgtReference]]] =
    collection
      .find(filter = Filters.equal(key, cgtReference.value))
      .toFuture()
      .map { json =>
        Right(json.map(_.`return`).toList)
      }
      .recover { case NonFatal(e) =>
        logger.warn(s"Not returning draft returns: ${e.getMessage}")
        Left(Error(e))
      }
}

object DefaultDraftReturnsRepository {

  final case class DraftReturnWithCgtReferenceWrapper(
    _id: String,
    lastUpdated: Instant,
    `return`: DraftReturnWithCgtReference
  )

  object DraftReturnWithCgtReferenceWrapper {
    implicit val dtf: Format[Instant]                                = MongoJavatimeFormats.instantFormat
    implicit val format: OFormat[DraftReturnWithCgtReferenceWrapper] = Json.format[DraftReturnWithCgtReferenceWrapper]
  }

  case class DraftReturnWithCgtReference(
    draftReturn: DraftReturn,
    cgtReference: CgtReference,
    draftId: UUID
  )

  object DraftReturnWithCgtReference {
    implicit val format: OFormat[DraftReturnWithCgtReference] = Json.format[DraftReturnWithCgtReference]
  }
}
