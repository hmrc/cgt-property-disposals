package uk.gov.hmrc.cgtpropertydisposals.repositories

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject}
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import reactivemongo.play.json.ImplicitBSONHandlers.JsObjectDocumentWriter
import uk.gov.hmrc.auth.core.retrieve.GGCredId
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.UpdateVerifiersRequest
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultTaxEnrolmentRepository])
trait VerifiersRepository {
  def insert(updateVerifiersRequest: UpdateVerifiersRequest): EitherT[Future, Error, Unit]
  def delete(ggCredId: GGCredId): EitherT[Future, Error, Int]
}

class DefaultVerifiersRepository @Inject()(mongo: ReactiveMongoComponent)(
  implicit ec: ExecutionContext
) extends ReactiveRepository[UpdateVerifiersRequest, BSONObjectID](
      collectionName = "update-verifiers-requests",
      mongo          = mongo.mongoConnector.db,
      UpdateVerifiersRequest.updateVerifiersFormat,
      ReactiveMongoFormats.objectIdFormats
    )
    with VerifiersRepository {

  override def indexes: Seq[Index] = Seq(
    Index(
      key  = Seq("ggCredId" → IndexType.Ascending),
      name = Some("ggCredIdIndex")
    )
  )

  override def insert(updateVerifiersRequest: UpdateVerifiersRequest): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      collection.insert
        .one[UpdateVerifiersRequest](updateVerifiersRequest)
        .map[Either[Error, Unit]] { result: WriteResult =>
          if (result.ok)
            Right(())
          else
            Left(
              Error(
                s"Could not insert update verifier request into database: got write errors :${result.writeErrors}"
              )
            )
        }
        .recover {
          case exception => Left(Error(exception))
        }
    )

  override def delete(ggCredId: GGCredId): EitherT[Future, Error, Int] =
    EitherT[Future, Error, Int](
      collection.delete
        .one(Json.obj("ggCredId" -> ggCredId.credId))
        .map { result: WriteResult =>
          Right(result.n)
        }
        .recover {
          case exception => Left(Error(exception.getMessage))
        }
    )

}
