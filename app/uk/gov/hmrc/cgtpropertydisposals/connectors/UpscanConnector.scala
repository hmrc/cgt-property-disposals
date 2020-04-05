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

package uk.gov.hmrc.cgtpropertydisposals.connectors

import java.util.UUID

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.HeaderNames.USER_AGENT
import play.api.libs.ws.ahc.AhcWSResponse
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack.UpscanSuccess
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@ImplementedBy(classOf[UpscanConnectorImpl])
trait UpscanConnector {
  def downloadFile(upscanSuccess: UpscanSuccess): Future[Either[Error, FileAttachment]]
}

@Singleton
class UpscanConnectorImpl @Inject() (playHttpClient: PlayHttpClient, config: ServicesConfig)(
  implicit executionContext: ExecutionContext
) extends UpscanConnector
    with Logging
    with HttpErrorFunctions {

  private lazy val userAgent: String = config.getConfString("appName", "cgt-property-disposal")
  private lazy val timeout: Duration = config.getDuration("dms.s3-file-download-timeout")

  private val headers: Seq[(String, String)] = Seq(USER_AGENT -> userAgent)

  override def downloadFile(upscanSuccess: UpscanSuccess): Future[Either[Error, FileAttachment]] =
    (upscanSuccess.uploadDetails.get("filename"), upscanSuccess.uploadDetails.get("fileMimeType")) match {
      case (Some(filename), Some(mimeType)) =>
        playHttpClient
          .get(upscanSuccess.downloadUrl, headers, timeout)
          .map {
            case AhcWSResponse(underlying) =>
              underlying.status match {
                case s if is4xx(s) | is5xx(s) => Left(Error(s"download failed with status $s"))
                case _ =>
                  Right(FileAttachment(UUID.randomUUID().toString, filename, Some(mimeType), underlying.bodyAsBytes))
              }
          }
          .recover {
            case NonFatal(e) => Left(Error(e))
          }
      case _ => Future.successful(Left(Error("missing file descriptors")))
    }

}
