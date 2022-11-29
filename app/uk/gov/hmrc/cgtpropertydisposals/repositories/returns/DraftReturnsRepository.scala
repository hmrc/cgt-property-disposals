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
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.mongodb.scala.model.Filters
import configs.syntax._
import org.mongodb.scala.model.Filters.{equal, or}
import play.api.Configuration
import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.functional.syntax._
import play.api.libs.json.Format.GenericFormat
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DefaultDraftReturnsRepository.DraftReturnWithCgtReference
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.util.UUID
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultDraftReturnsRepository])
trait DraftReturnsRepository {
  def fetch(cgtReference: CgtReference): EitherT[Future, Error, Seq[DraftReturn]]

  def save(draftReturn: DraftReturn, cgtReference: CgtReference): EitherT[Future, Error, Unit]

  def delete(cgtReference: CgtReference): EitherT[Future, Error, Unit]

  def deleteAll(draftReturnIds: List[UUID]): EitherT[Future, Error, Unit]
}

@Singleton
class DefaultDraftReturnsRepository @Inject() (mongo: MongoComponent, config: Configuration)(implicit
  val ec: ExecutionContext
) extends PlayMongoRepository[DraftReturnWithCgtReference](
      mongoComponent = mongo,
      collectionName = "draft-returns",
      domainFormat = DraftReturnWithCgtReference.format,
      indexes = Seq()
    )
    with DraftReturnsRepository
    with CacheRepository[DraftReturnWithCgtReference] {

  val cacheTtl: FiniteDuration  = config.underlying.get[FiniteDuration]("mongodb.draft-returns.expiry-time").value
  val maxDraftReturns: Int      = config.underlying.get[Int]("mongodb.draft-returns.max-draft-returns").value
  val cacheTtlIndexName: String = "draft-return-cache-ttl"
  val objName: String           = "return"
  val key: String               = "return.cgtReference.value"

  override def fetch(cgtReference: CgtReference): EitherT[Future, Error, Seq[DraftReturn]] =
    EitherT(
      preservingMdc {
        get(cgtReference)
      }
    ).map(_.map(_.draftReturn))

  override def save(draftReturn: DraftReturn, cgtReference: CgtReference): EitherT[Future, Error, Unit] =
    EitherT(
      preservingMdc {
        set(draftReturn.id.toString, DraftReturnWithCgtReference(draftReturn, cgtReference, draftReturn.id))
      }
    )

  override def delete(cgtReference: CgtReference): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      preservingMdc {
        collection
          .deleteOne(equal("return.cgtReference.value", cgtReference.value))
          .toFuture()
//        remove("return.cgtReference.value" -> cgtReference.value)
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
//                collection.deleteOne(or(equal("return.draftId", "1"), equal("return.draftId", "2"))).toFuture()

        collection
          .deleteOne(
            or(draftReturnIds.map(id => equal("return.draftId", id)): _*)
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

  private def get(cgtReference: CgtReference): Future[Either[Error, Seq[DraftReturnWithCgtReference]]] =
    collection
      .find(filter = Filters.equal(key, cgtReference.value))
      .toFuture
      .map { json =>
        Right(json)
      }
}

object DefaultDraftReturnsRepository {

  final case class DraftReturnWithCgtReference(draftReturn: DraftReturn, cgtReference: CgtReference, draftId: UUID)

  object DraftReturnWithCgtReference {

//    val format: Format[DraftReturnWithCgtReference] =
//      ((__ \ "draftReturn").format[DraftReturn]
//        ~ (__ \ "cgtReference").format[CgtReference]
//        ~ (__ \ "draftId").format[UUID])(DraftReturnWithCgtReference.apply, unlift(DraftReturnWithCgtReference.unapply))

    val reads: Reads[DraftReturnWithCgtReference] =
      (
        (JsPath \ "draftReturn").read[DraftReturn] and
          (JsPath \ "cgtReference").read[CgtReference] and
          (JsPath \ "draftId").read[UUID]
      )(DraftReturnWithCgtReference.apply _)

    val writes: OWrites[DraftReturnWithCgtReference] =
      (
        (JsPath \ "draftReturn").write[DraftReturn] and
          (JsPath \ "cgtReference").write[CgtReference] and
          (JsPath \ "draftId").write[UUID]
      )(unlift(DraftReturnWithCgtReference.unapply))

    implicit val format: OFormat[DraftReturnWithCgtReference] = OFormat(reads, writes)
  }
}
