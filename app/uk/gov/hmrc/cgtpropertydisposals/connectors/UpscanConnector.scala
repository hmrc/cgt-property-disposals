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

import akka.util.ByteString
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.HeaderNames.USER_AGENT
import play.api.libs.ws.ahc.AhcWSResponse
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.FileAttachment
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanCallBack
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HttpErrorFunctions
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[UpscanConnectorImpl])
trait UpscanConnector {
  def downloadFile(url: UpscanCallBack): Future[Either[Error, FileAttachment]]
}

@Singleton
class UpscanConnectorImpl @Inject() (playHttpClient: PlayHttpClient, config: ServicesConfig)(
  implicit executionContext: ExecutionContext
) extends UpscanConnector
    with Logging
    with HttpErrorFunctions {

  private lazy val userAgent: String = config.getConfString("appName", "cgt-property-disposal")

  private val headers: Seq[(String, String)] = Seq(USER_AGENT -> userAgent)

  override def downloadFile(upscanCallBack: UpscanCallBack): Future[Either[Error, FileAttachment]] =
    upscanCallBack.downloadUrl match {
      case Some(url) => {
        playHttpClient
          .get(url, headers, 2 minutes)
          .map {
            case AhcWSResponse(underlying) =>
              underlying.status match {
                case s if is4xx(s) | is5xx(s) => Left(Error(s"download failed with status $s"))
                case _                        => makeFileAttachment(upscanCallBack, underlying.bodyAsBytes)
              }
          }
      }
      case None => Future.successful(Left(Error("No download url")))
    }

  private def makeFileAttachment(upscanCallBack: UpscanCallBack, data: ByteString): Either[Error, FileAttachment] =
    (upscanCallBack.details.get("filename"), upscanCallBack.details.get("fileMimeType")) match {
      case (Some(filename), Some(mimeType)) =>
        Right(FileAttachment(UUID.randomUUID().toString, filename, Some(mimeType), data))
      case _ => Left(Error("missing file descriptors"))
    }

}
