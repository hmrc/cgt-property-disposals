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
import configs.syntax._
import org.mongodb.scala.model.Filters.{equal, or}
import play.api.Configuration
import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ListUtils._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.repositories.CacheRepository
import uk.gov.hmrc.cgtpropertydisposals.repositories.returns.DefaultDraftReturnsRepository.DraftReturnWithCgtReference
import uk.gov.hmrc.cgtpropertydisposals.util.JsErrorOps._
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.Codecs.logger
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.util.UUID
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[DefaultDraftReturnsRepository])
trait DraftReturnsRepository {
  def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[DraftReturn]]

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

  override def fetch(cgtReference: CgtReference): EitherT[Future, Error, List[DraftReturn]] =
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

//        remove(
//          "$or" -> JsArray(
//            draftReturnIds.map(id => JsObject(Map("return.draftId" -> JsString(id.toString))))
//          )
//        )
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
      .find(equal(key, cgtReference.value))
      .toFuture()
      .map[Either[Error, List[DraftReturnWithCgtReference]]] { returns =>
//        val p: List[Either[Error, DraftReturnWithCgtReference]] = l.map { json =>
//          (json \ objName).validate[DraftReturnWithCgtReference].asEither.leftMap(toError)
//        }
//        val (errors, draftReturns)                              = p.partitionWith(identity)
//        if (errors.nonEmpty)

        Right(returns.toList)
      }
      .recover { case NonFatal(e) =>
        logger.warn(s"Not returning draft returns: ${e.getMessage}")
        Left(Error(e))
      }

  private def toError(e: Seq[(JsPath, Seq[JsonValidationError])]): Error =
    Error(JsError(e).prettyPrint())

}

object DefaultDraftReturnsRepository {

  final case class DraftReturnWithCgtReference(draftReturn: DraftReturn, cgtReference: CgtReference, draftId: UUID)

  object DraftReturnWithCgtReference {

    implicit val format: OFormat[DraftReturnWithCgtReference] = Json.format

  }
}
