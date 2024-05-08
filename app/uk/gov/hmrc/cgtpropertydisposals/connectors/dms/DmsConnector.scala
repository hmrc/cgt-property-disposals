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
import com.google.inject.{Inject, Singleton}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.cgtpropertydisposals.http.PlayHttpClient
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{DmsSubmissionPayload, EnvelopeId}
import uk.gov.hmrc.cgtpropertydisposals.service.PdfGeneratorService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.{Base64, UUID}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

@Singleton
class DmsConnector @Inject() (
  httpClient: PlayHttpClient,
  servicesConfig: ServicesConfig,
  pdfGeneratorService: PdfGeneratorService
)(implicit
  ex: ExecutionContext
) extends Logging {
  private val cgtPropertyDisposalsUrl = servicesConfig.baseUrl("cgt-property-disposals")
  private val url                     = s"${servicesConfig.baseUrl("dms")}/dms-submission/submit"
  private val headers                 = Seq(AUTHORIZATION -> s"${servicesConfig.getString("internal-auth.token")}")

  def submitToDms(
    dmsSubmissionPayload: DmsSubmissionPayload,
    id: UUID
  ): EitherT[Future, Error, EnvelopeId] = {
    val dataParts: Seq[MultipartFormData.DataPart] = Seq(
      MultipartFormData.DataPart("callbackUrl", s"$cgtPropertyDisposalsUrl/cgt-property-disposals/dms/callback"),
      MultipartFormData.DataPart("submissionReference", ""),
      MultipartFormData.DataPart("metadata.source", "cgtpd"),
      MultipartFormData.DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now())),
      MultipartFormData.DataPart("metadata.formId", dmsSubmissionPayload.dmsMetadata.dmsFormId),
      MultipartFormData.DataPart("metadata.customerId", dmsSubmissionPayload.dmsMetadata.customerId),
      MultipartFormData.DataPart("metadata.classificationType", dmsSubmissionPayload.dmsMetadata.classificationType),
      MultipartFormData.DataPart("metadata.businessArea", dmsSubmissionPayload.dmsMetadata.businessArea)
    )

    val pdfBytes = pdfGeneratorService.generatePDFBytesLocal(decode(dmsSubmissionPayload.b64Html.value))

    val fileParts: Seq[MultipartFormData.FilePart[Source[ByteString, _]]] =
      Seq(
        MultipartFormData.FilePart(
          key = "form",
          filename = "form.pdf",
          contentType = Some("application/octet-stream"),
          ref = Source.single(ByteString(pdfBytes))
        )
      )

    val attachmentParts: Seq[MultipartFormData.FilePart[Source[ByteString, _]]] = dmsSubmissionPayload.attachments.map {
      attachment =>
        MultipartFormData.FilePart(
          key = "attachment",
          filename = attachment.filename,
          contentType = Some("application/octet-stream"),
          ref = Source.apply(attachment.data)
        )
    }

    val source: Source[MultipartFormData.Part[Source[ByteString, _]], NotUsed] = Source(
      dataParts ++ fileParts ++ attachmentParts
    )

    logger.info("Sending payload to dms service...")
    EitherT(
      httpClient
        .post(url, headers, source)
        .map { response =>
          response.status match {
            case 202                                      =>
              logger.info("Successfully submitted form data to DMS")
              Right(EnvelopeId(response.body))
            case status if is4xx(status) || is5xx(status) =>
              logger.warn(s"Bad response status from dms service ${response.status}")
              Left(Error(response.body))
            case _                                        =>
              logger.warn(response.body)
              logger.warn("could not send payload to dms service")
              Left(Error("Invalid HTTP response status from dms service"))
          }
        }
        .recover { case NonFatal(e) =>
          logger.warn(s"failed to send payload to dms service due to http exception: $e")
          Left(Error("http exception"))
        }
    )
  }

  private val decode = (s: String) => new String(Base64.getDecoder.decode(s))

  private def is4xx(status: Int) = status >= 400 && status < 500

  private def is5xx(status: Int) = status >= 500 && status < 600

}
