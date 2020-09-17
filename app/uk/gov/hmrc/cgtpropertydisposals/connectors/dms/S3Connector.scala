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

package uk.gov.hmrc.cgtpropertydisposals.connectors.dms

import java.util.UUID

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, ActorMaterializerSettings}
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.HeaderNames.USER_AGENT
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.service.dms.DmsSubmissionPollerExecutionContext
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

@ImplementedBy(classOf[S3ConnectorImpl])
trait S3Connector {
  def downloadFile(upscanSuccess: UpscanSuccess): Future[Either[Error, FileAttachment]]
}

@Singleton
class S3ConnectorImpl @Inject() (
  actorSystem: ActorSystem,
  playHttpClient: PlayHttpClient,
  config: ServicesConfig
)(implicit
  executionContext: DmsSubmissionPollerExecutionContext,
  system: ActorSystem
) extends S3Connector
    with Logging
    with HttpErrorFunctions {

  implicit val mat                           = ActorMaterializer(
    ActorMaterializerSettings(actorSystem).withDispatcher("dms-submission-poller-dispatcher")
  )
  private lazy val userAgent: String         = config.getConfString("appName", "cgt-property-disposals")
  private lazy val maxFileDownloadSize       = config.getConfInt("s3.max-file-download-size-in-mb", 5).toLong
  private val limitScaleFactor               = config.getConfInt("s3.upstream-element-limit-scale-factor", 200).toLong
  private lazy val timeout: Duration         = config.getDuration("s3.file-download-timeout")
  private val headers: Seq[(String, String)] = Seq(USER_AGENT -> userAgent)

  override def downloadFile(upscanSuccess: UpscanSuccess): Future[Either[Error, FileAttachment]] = {
    logger.info(s"Downloading files from S3")
    (upscanSuccess.uploadDetails.get("fileName"), upscanSuccess.uploadDetails.get("fileMimeType")) match {
      case (Some(filename), Some(mimeType)) =>
        playHttpClient
          .get(upscanSuccess.downloadUrl, headers, timeout)
          .flatMap { response =>
            response.status match {
              case status if is4xx(status) | is5xx(status) =>
                logger.warn(
                  s"could not download file from s3 : ${response.toString}" +
                    s"download url : ${upscanSuccess.downloadUrl}" +
                    s"http status: ${response.status}" +
                    s"http body: ${response.body}"
                )
                Future.successful(Left(Error("could not download file from s3")))
              case _                                       =>
                response.bodyAsSource
                  .limit(maxFileDownloadSize * limitScaleFactor)
                  .runWith(Sink.seq)
                  .map { bytes =>
                    logger.info("Successfully downloaded files from S3")
                    Right(
                      FileAttachment(
                        UUID.randomUUID().toString,
                        filename,
                        Some(mimeType),
                        bytes
                      )
                    )
                  }
            }
          }
          .recover { case NonFatal(e) =>
            Left(Error(e))
          }
      case _                                =>
        logger.warn(s"could not find file name nor mime type : $upscanSuccess")
        Future.successful(Left(Error("missing file descriptors")))
    }
  }
}
