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

package uk.gov.hmrc.cgtpropertydisposals.connectors.dms

import akka.stream.Materializer
import akka.stream.scaladsl.Sink
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

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.control.NonFatal

@ImplementedBy(classOf[S3ConnectorImpl])
trait S3Connector {
  def downloadFile(upscanSuccess: UpscanSuccess): Future[Either[Error, FileAttachment]]
}

@Singleton
class S3ConnectorImpl @Inject() (
  playHttpClient: PlayHttpClient,
  config: ServicesConfig
)(implicit
  executionContext: DmsSubmissionPollerExecutionContext,
  materializer: Materializer
) extends S3Connector
    with Logging
    with HttpErrorFunctions {

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
                      replaceAllInvalidCharsWithHyphen(
                        FileAttachment(
                          UUID.randomUUID().toString,
                          filename,
                          Some(mimeType),
                          bytes
                        )
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

  // It replaces all invalid characters available in Filename with hyphen(_)
  // to avoid issues with Windows OS
  private def replaceAllInvalidCharsWithHyphen(f: FileAttachment): FileAttachment = {
    // \x00-\x1F ==> [1-32]
    val invalidASCIIChars   = (0 to 31).map(_.toString).toList ++: "00,01,02,03,04,05,06,07,08,09".split(",").toList
    val invalidSpecialChars = "[%<>:/\"|?*\\\\]".r

    val filenameWithExtension = f.filename.split("\\.(?=[^\\.]+$)")

    val updatedFilename =
      if (invalidASCIIChars.contains(filenameWithExtension(0))) "-"
      else invalidSpecialChars.replaceAllIn(filenameWithExtension(0), "-")

    val fullUpdatedFilename =
      if (filenameWithExtension.length > 1) s"$updatedFilename.${filenameWithExtension(1)}"
      else updatedFilename

    f.copy(filename = fullUpdatedFilename)
  }

}
