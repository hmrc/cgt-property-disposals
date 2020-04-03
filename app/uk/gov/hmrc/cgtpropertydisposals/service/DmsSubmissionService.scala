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

package uk.gov.hmrc.cgtpropertydisposals.service

import java.util.Base64

import cats.data.EitherT
import cats.instances.either._
import cats.instances.future._
import cats.instances.list._
import cats.syntax.traverse._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.Configs
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.connectors.GFormConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, DraftReturnId}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[DefaultDmsSubmissionService])
trait DmsSubmissionService {

  def submitToDms(html: B64Html, draftReturnId: DraftReturnId, cgtReference: CgtReference, formBundleId: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, EnvelopeId]

}

@Singleton
class DefaultDmsSubmissionService @Inject() (
  gFormConnector: GFormConnector,
  upscanService: UpscanService,
  configuration: Configuration
)(implicit ec: ExecutionContext)
    extends DmsSubmissionService
    with Logging {

  def getDmsMetaConfig[A: Configs](key: String): A =
    configuration.underlying
      .get[A](s"dms.$key")
      .value

  val queue: String           = getDmsMetaConfig[String]("queue-name")
  val b64businessArea: String = getDmsMetaConfig[String]("b64-business-area")

  val businessArea = new String(Base64.getDecoder.decode(b64businessArea))

  @SuppressWarnings(Array("org.wartremover.warts.Any"))
  override def submitToDms(
    html: B64Html,
    draftReturnId: DraftReturnId,
    cgtReference: CgtReference,
    formBundleId: String
  )(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, EnvelopeId] = {

    val fileUploadResult: EitherT[Future, Error, EnvelopeId] = for {
      upscanSnapshot  <- upscanService.getUpscanSnapshot(draftReturnId)
      callbacks       <- upscanService.getAllUpscanCallBacks(draftReturnId)
      attachments     <- upscanService.downloadFilesFromS3(upscanSnapshot, callbacks)
      fileAttachments <- EitherT.fromEither[Future](attachments.sequence)
      envId <- gFormConnector.submitToDms(
                DmsSubmissionPayload(
                  html,
                  fileAttachments,
                  DmsMetadata(formBundleId, cgtReference.value, queue, businessArea)
                )
              )
    } yield envId

    fileUploadResult

  }

}
