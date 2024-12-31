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

package uk.gov.hmrc.cgtpropertydisposals.repositories.enrolments

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import com.mongodb.client.model.Indexes.ascending
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model._
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.{Codecs, PlayMongoRepository}
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
class DefaultTaxEnrolmentRepository @Inject() (mongo: MongoComponent)(implicit
  ec: ExecutionContext
) extends PlayMongoRepository[TaxEnrolmentRequest](
      mongoComponent = mongo,
      collectionName = "tax-enrolment-requests",
      domainFormat = TaxEnrolmentRequest.format,
      indexes = Seq(IndexModel(ascending("ggCredId"), IndexOptions().name("ggCredIdIndex"))),
      extraCodecs = Seq(Codecs.playFormatCodec[TaxEnrolmentRequest](TaxEnrolmentRequest.format))
    )
    with TaxEnrolmentRepository {

  override def save(cgtEnrolmentRequest: TaxEnrolmentRequest): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      preservingMdc {
        collection
          .insertOne(cgtEnrolmentRequest)
          .toFuture()
          .map[Either[Error, Unit]] { result =>
            if (result.wasAcknowledged()) {
              Right(())
            } else {
              Left(
                Error(
                  s"Could not insert enrolment request into database: got write errors :$result"
                )
              )
            }
          }
          .recover { case exception =>
            Left(Error(exception))
          }
      }
    )

  override def get(ggCredId: String): EitherT[Future, Error, Option[TaxEnrolmentRequest]] =
    EitherT[Future, Error, Option[TaxEnrolmentRequest]](
      preservingMdc {
        collection
          .find(equal("ggCredId", ggCredId))
          .toFuture()
          .map(maybeEnrolmentRequest => Right(maybeEnrolmentRequest.headOption))
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )

  override def delete(ggCredId: String): EitherT[Future, Error, Int] =
    EitherT[Future, Error, Int](
      preservingMdc {
        collection
          .deleteOne(equal("ggCredId", ggCredId))
          .toFuture()
          .map(result => Right(result.getDeletedCount.intValue()))
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
        collection
          .findOneAndUpdate(
            filter = equal("ggCredId", ggCredId),
            update = Updates.combine(
              Updates.set("ggCredId", cgtEnrolmentRequest.ggCredId),
              Updates.set("cgtReference", cgtEnrolmentRequest.cgtReference),
              Updates.set("address", Codecs.toBson(cgtEnrolmentRequest.address)),
              Updates.set("timestamp", Codecs.toBson(cgtEnrolmentRequest.timestamp))
            ),
            options = FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER)
          )
          .toFutureOption()
          .map {
            case dbResult @ Some(_) => Right(dbResult)
            case None               => Left(Error("was not able to update"))
          }
          .recover { case exception =>
            Left(Error(exception.getMessage))
          }
      }
    )
}
