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

import java.io.File
import java.nio.file.{Files, _}

import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import cats.data.EitherT
import cats.implicits._
import com.google.inject.Inject
import play.api.http.DefaultWriteables
import play.api.libs.Files.{SingletonTemporaryFileCreator, TemporaryFile}
import play.api.mvc.MultipartFormData
import play.api.mvc.MultipartFormData.FilePart
import uk.gov.hmrc.cgtpropertydisposals.connectors.GFormConnector._
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{DmsSubmissionPayload, EnvelopeId, FileAttachment}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait GFormConnector {
  def submitToDms(dmsSubmission: DmsSubmissionPayload)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, EnvelopeId]
}

class GFormConnectorImpl @Inject() (playHttpClient: PlayHttpClient, servicesConfig: ServicesConfig)(
  implicit ex: ExecutionContext
) extends DefaultWriteables
    with GFormConnector
    with Logging {

  val gformUrl: String = s"${servicesConfig.baseUrl("gform")}/dms/submit-with-attachments"

  @SuppressWarnings(
    Array("org.wartremover.warts.Var", "org.wartremover.warts.Any", "org.wartremover.warts.PublicInference")
  )
  override def submitToDms(
    dmsSubmissionPayload: DmsSubmissionPayload
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, EnvelopeId] = {

    def sendFormdata(
      p: Source[MultipartFormData.Part[Source[ByteString, _]], _]
    ): EitherT[Future, Error, EnvelopeId] =
      EitherT[Future, Error, EnvelopeId](
        playHttpClient
          .post(gformUrl, hc.headers, p)
          .map { response =>
            response.status match {
              case 200 => Right(EnvelopeId(response.body))
              case status if (is4xx(status) || is5xx(status)) =>
                logger.warn(s"Bad response status from gform service ${response.body}")
                Left(Error(response.body))
              case _ => Left(Error("Invalid HTTP response status from gform service"))
            }
          }
      )

    for {
      fileparts <- EitherT.fromOption[Future](
                    makeTemporaryFiles(dmsSubmissionPayload),
                    Error("Could not construct temporary files")
                  )
      formdata   <- EitherT.pure[Future, Error](createFormData(dmsSubmissionPayload, fileparts))
      payload    <- EitherT.pure[Future, Error](convertToPayload(formdata))
      envelopeId <- sendFormdata(payload)
    } yield envelopeId

  }

  private def is4xx(status: Int): Boolean = status >= 400 && status < 500
  private def is5xx(status: Int): Boolean = status >= 500 && status < 600
}

@SuppressWarnings(
  Array("org.wartremover.warts.Var", "org.wartremover.warts.Any", "org.wartremover.warts.PublicInference")
)
object GFormConnector {

  private def suffix(contentType: Option[String]): String =
    contentType match {
      case Some("application/pdf")                                                   => ".pdf"
      case Some("application/xml")                                                   => ".xml"
      case Some("application/json")                                                  => ".json"
      case Some("image/jpeg")                                                        => ".jpeg"
      case Some("image/png")                                                         => ".png"
      case Some("text/xml")                                                          => ".xml"
      case Some("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") => ".xlsx"
      case Some("application/vnd.ms-excel")                                          => ".xls"
      case Some("application/msword")                                                => ".doc"
      case Some(_)                                                                   => ".txt"
      case None                                                                      => ".txt"
    }

  def createTempFile(prefix: String, suffix: String, data: Array[Byte]): Option[Path] =
    Try {
      val tmpFile = File.createTempFile(prefix, suffix)
      Files.write(tmpFile.toPath, data)
    }.toOption

  def createFilePart(attachment: FileAttachment, path: Path)(implicit hc: HeaderCarrier): FilePart[TemporaryFile] =
    FilePart(
      key         = hc.sessionId.map(_.value).getOrElse("").take(10),
      filename    = attachment.filename,
      contentType = attachment.contentType,
      ref         = SingletonTemporaryFileCreator.create(path)
    )

  def processAttachment(attachment: FileAttachment)(implicit hc: HeaderCarrier): Option[FilePart[TemporaryFile]] =
    createTempFile(attachment.filename, suffix(attachment.contentType), attachment.data.toArray).map { path =>
      createFilePart(attachment, path)
    }

  def filePartToByteString(fileparts: Seq[FilePart[TemporaryFile]]): Seq[FilePart[Source[ByteString, Any]]] =
    fileparts.map(file => file.copy(ref = FileIO.fromPath(file.ref.path): Source[ByteString, Any]))

  def makeTemporaryFiles(
    dmsSubmissionPayload: DmsSubmissionPayload
  )(implicit hc: HeaderCarrier): Option[List[FilePart[TemporaryFile]]] =
    dmsSubmissionPayload.attachments.traverse(f => processAttachment(f))

  def createFormData(
    dmsSubmissionPayload: DmsSubmissionPayload,
    temporaryFiles: List[FilePart[TemporaryFile]]
  ): MultipartFormData[TemporaryFile] =
    MultipartFormData[TemporaryFile](
      dataParts = Map(
        "html"               -> Seq(dmsSubmissionPayload.b64Html.value),
        "dmsFormId"          -> Seq(dmsSubmissionPayload.dmsMetadata.dmsFormId),
        "customerId"         -> Seq(dmsSubmissionPayload.dmsMetadata.customerId),
        "classificationType" -> Seq(dmsSubmissionPayload.dmsMetadata.classificationType),
        "businessArea"       -> Seq(dmsSubmissionPayload.dmsMetadata.businessArea)
      ),
      files    = temporaryFiles,
      badParts = Nil
    )

  def convertToPayload(
    formData: MultipartFormData[TemporaryFile]
  ): Source[MultipartFormData.Part[Source[ByteString, _]], _] =
    Source.apply(formData.dataParts.flatMap {
      case (key, values) =>
        values.map(value => MultipartFormData.DataPart(key, value): MultipartFormData.Part[Source[ByteString, _]])
    } ++ filePartToByteString(formData.files))

}
