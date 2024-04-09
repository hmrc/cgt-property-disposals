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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.Sink
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
    val result = for {
      filename <-
        EitherT.fromOption[Future](upscanSuccess.uploadDetails.get("fileName"), Error("missing file descriptors"))
      mimeType <-
        EitherT.fromOption[Future](upscanSuccess.uploadDetails.get("fileMimeType"), Error("missing file descriptors"))
      response <- EitherT.right[Error](playHttpClient.get(upscanSuccess.downloadUrl, headers, timeout))
      _        <-
        if (is4xx(response.status) || is5xx(response.status)) {
          EitherT.leftT[Future, Unit](Error("could not download file from s3"))
        } else {
          EitherT.rightT[Future, Error](())
        }
      bytes    <-
        EitherT.right[Error](response.bodyAsSource.limit(maxFileDownloadSize * limitScaleFactor).runWith(Sink.seq))
    } yield {
      logger.info("Successfully downloaded files from S3")
      replaceAllInvalidCharsWithHyphen(FileAttachment(UUID.randomUUID().toString, filename, Some(mimeType), bytes))
    }
    result.value
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
