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

package uk.gov.hmrc.cgtpropertydisposals.repositories.dms

import java.time.Clock

import cats.data.EitherT
import com.google.inject.ImplementedBy
import javax.inject.{Inject, Singleton}
import org.joda.time.DateTime
import play.api.Configuration
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionRequest
import uk.gov.hmrc.cgtpropertydisposals.util.TimeOps._
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.workitem._

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultDmsSubmissionRepo])
trait DmsSubmissionRepo {
  def set(dmsSubmissionRequest: DmsSubmissionRequest): EitherT[Future, Error, WorkItem[DmsSubmissionRequest]]
  def get: EitherT[Future, Error, Option[WorkItem[DmsSubmissionRequest]]]
  def setProcessingStatus(
    id: BSONObjectID,
    status: ProcessingStatus
  ): EitherT[Future, Error, Boolean]
  def setResultStatus(id: BSONObjectID, status: ResultStatus): EitherT[Future, Error, Boolean]
}

@Singleton
class DefaultDmsSubmissionRepo @Inject() (
  reactiveMongoComponent: ReactiveMongoComponent,
  configuration: Configuration,
  servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext)
    extends WorkItemRepository[DmsSubmissionRequest, BSONObjectID](
      collectionName = "dms-submission-request-work-item",
      mongo = reactiveMongoComponent.mongoConnector.db,
      itemFormat = DmsSubmissionRequest.workItemFormat,
      configuration.underlying
    )
    with DmsSubmissionRepo {

  override def now: DateTime = Clock.systemUTC().nowAsJoda

  override def workItemFields: WorkItemFieldNames =
    new WorkItemFieldNames {
      val receivedAt   = "receivedAt"
      val updatedAt    = "updatedAt"
      val availableAt  = "availableAt"
      val status       = "status"
      val id           = "_id"
      val failureCount = "failureCount"
    }

  override def inProgressRetryAfterProperty: String = "dms.submission-poller.in-progress-retry-after"

  private lazy val ttl = servicesConfig.getDuration("dms.submission-poller.mongo.ttl").toSeconds

  private val retryPeriod = inProgressRetryAfter.getMillis.toInt

  override def indexes: Seq[Index] =
    super.indexes ++ Seq(
      Index(
        key = Seq("receivedAt" -> IndexType.Ascending),
        name = Some("receivedAtTime"),
        options = BSONDocument("expireAfterSeconds" -> ttl)
      )
    )

  override def set(dmsSubmissionRequest: DmsSubmissionRequest): EitherT[Future, Error, WorkItem[DmsSubmissionRequest]] =
    EitherT[Future, Error, WorkItem[DmsSubmissionRequest]](
      pushNew(dmsSubmissionRequest, now, (_: DmsSubmissionRequest) => ToDo).map(item => Right(item)).recover {
        case exception: Exception => Left(Error(exception))
      }
    )

  override def get: EitherT[Future, Error, Option[WorkItem[DmsSubmissionRequest]]] =
    EitherT[Future, Error, Option[WorkItem[DmsSubmissionRequest]]](
      super
        .pullOutstanding(failedBefore = now.minusMillis(retryPeriod), availableBefore = now)
        .map(workItem => Right(workItem))
        .recover {
          case exception: Exception => Left(Error(exception))
        }
    )

  override def setProcessingStatus(
    id: BSONObjectID,
    status: ProcessingStatus
  ): EitherT[Future, Error, Boolean] =
    EitherT[Future, Error, Boolean](
      markAs(id, status, Some(now.plusMillis(retryPeriod)))
        .map(result => Right(result))
        .recover {
          case exception: Exception => Left(Error(exception))
        }
    )

  override def setResultStatus(id: BSONObjectID, status: ResultStatus): EitherT[Future, Error, Boolean] =
    EitherT[Future, Error, Boolean](complete(id, status).map(result => Right(result)).recover {
      case exception: Exception => Left(Error(exception))
    })
}
