/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.WriteResult
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultTaxEnrolmentRepository])
trait TaxEnrolmentRepository {
  def get(ggCredId: String): EitherT[Future, Error, Option[TaxEnrolmentRequest]]
  def save(cgtEnrolmentRequest: TaxEnrolmentRequest): EitherT[Future, Error, Unit]
  def delete(userId: String): EitherT[Future, Error, Int]
  def update(
    ggCredId: String,
    cgtEnrolmentRequest: TaxEnrolmentRequest
  ): EitherT[Future, Error, Option[TaxEnrolmentRequest]]
}

@Singleton
class DefaultTaxEnrolmentRepository @Inject() (mongo: ReactiveMongoComponent)(implicit
  ec: ExecutionContext
) extends ReactiveRepository[TaxEnrolmentRequest, BSONObjectID](
      collectionName = "tax-enrolment-requests",
      mongo = mongo.mongoConnector.db,
      TaxEnrolmentRequest.format,
      ReactiveMongoFormats.objectIdFormats
    )
    with TaxEnrolmentRepository {

  override def indexes: Seq[Index] =
    Seq(
      Index(
        key = Seq("ggCredId" → IndexType.Ascending),
        name = Some("ggCredIdIndex")
      )
    )

  override def save(cgtEnrolmentRequest: TaxEnrolmentRequest): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      preservingMdc {
        insert(cgtEnrolmentRequest)
          .map[Either[Error, Unit]] { result: WriteResult =>
            if (result.ok)
              Right(())
            else
              Left(
                Error(
                  s"Could not insert enrolment request into database: got write errors :${result.writeErrors}"
                )
              )
          }
          .recover { case exception =>
            Left(Error(exception))
          }
      }
    )

  override def get(ggCredId: String): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    EitherT[Future, Error, Option[TaxEnrolmentRequest]](
      preservingMdc {
        find("ggCredId" -> ggCredId)
          .map(maybeEnrolmentRequest => Right(maybeEnrolmentRequest.headOption))
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

  override def delete(ggCredId: String): EitherT[Future, Error, Int] =
    EitherT[Future, Error, Int](
      preservingMdc {
        remove("ggCredId" -> ggCredId)
          .map { result: WriteResult => Right(result.n) }
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

  override def update(
    ggCredId: String,
    cgtEnrolmentRequest: TaxEnrolmentRequest
  ): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    EitherT[Future, Error, Option[TaxEnrolmentRequest]](
      preservingMdc {
        findAndUpdate(
          Json.obj("ggCredId" -> ggCredId),
          Json.obj("$set"     -> Json.toJson(cgtEnrolmentRequest)),
          fetchNewObject = true
        ).map(dbResult => Right(dbResult.result[TaxEnrolmentRequest]))
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

}
