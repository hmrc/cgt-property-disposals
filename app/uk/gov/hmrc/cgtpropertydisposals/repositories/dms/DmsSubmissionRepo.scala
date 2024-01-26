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

package uk.gov.hmrc.cgtpropertydisposals.repositories.dms

import cats.data.EitherT
import com.google.inject.ImplementedBy
import org.bson.types.ObjectId
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionRequest
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.workitem.ProcessingStatus.ToDo
import uk.gov.hmrc.mongo.workitem._
import uk.gov.hmrc.play.http.logging.Mdc.preservingMdc

import java.time.{Duration, Instant}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultDmsSubmissionRepo])
trait DmsSubmissionRepo {
  def set(dmsSubmissionRequest: DmsSubmissionRequest): EitherT[Future, Error, WorkItem[DmsSubmissionRequest]]

  def get: EitherT[Future, Error, Option[WorkItem[DmsSubmissionRequest]]]

  def setProcessingStatus(
    id: ObjectId,
    status: ProcessingStatus
  ): EitherT[Future, Error, Boolean]

  def setResultStatus(id: ObjectId, status: ResultStatus): EitherT[Future, Error, Boolean]
}

@Singleton
class DefaultDmsSubmissionRepo @Inject() (
  mongo: MongoComponent,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends WorkItemRepository[DmsSubmissionRequest](
      collectionName = "dms-submission-request-work-item",
      mongoComponent = mongo,
      itemFormat = DmsSubmissionRequest.dmsSubmissionRequestFormat,
      workItemFields = WorkItemFields.default
    )
    with DmsSubmissionRepo {

  override def now(): Instant =
    Instant.now()

  override def inProgressRetryAfter: Duration = Duration.ofMillis(configuration.getMillis(inProgressRetryAfterProperty))

  private def inProgressRetryAfterProperty = "dms.submission-poller.in-progress-retry-after"

  private val retryPeriod = inProgressRetryAfter.getSeconds

  override def set(dmsSubmissionRequest: DmsSubmissionRequest): EitherT[Future, Error, WorkItem[DmsSubmissionRequest]] =
    EitherT[Future, Error, WorkItem[DmsSubmissionRequest]](
      preservingMdc {
        pushNew(dmsSubmissionRequest, now(), (_: DmsSubmissionRequest) => ToDo).map(item => Right(item)).recover {
          case exception: Exception => Left(Error(exception))
        }
      }
    )

  override def get: EitherT[Future, Error, Option[WorkItem[DmsSubmissionRequest]]] =
    EitherT[Future, Error, Option[WorkItem[DmsSubmissionRequest]]](
      preservingMdc {
        super
          .pullOutstanding(failedBefore = now().minusMillis(retryPeriod), availableBefore = now())
          .map(workItem => Right(workItem))
          .recover { case exception: Exception =>
            Left(Error(exception))
          }
      }
    )

  override def setProcessingStatus(
    id: ObjectId,
    status: ProcessingStatus
  ): EitherT[Future, Error, Boolean] =
    EitherT[Future, Error, Boolean](
      preservingMdc {
        markAs(id, status, Some(now().plusMillis(retryPeriod)))
          .map(result => Right(result))
          .recover { case exception: Exception =>
            Left(Error(exception))
          }
      }
    )

  override def setResultStatus(id: ObjectId, status: ResultStatus): EitherT[Future, Error, Boolean] =
    EitherT[Future, Error, Boolean](
      preservingMdc {
        complete(id, status).map(result => Right(result)).recover { case exception: Exception =>
          Left(Error(exception))
        }
      }
    )
}
