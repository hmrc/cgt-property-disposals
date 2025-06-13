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

import com.google.inject.{Inject, Singleton}
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import play.api.http.HeaderNames.AUTHORIZATION
import play.api.mvc.MultipartFormData
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{DmsEnvelopeId, DmsSubmissionPayload}
import uk.gov.hmrc.cgtpropertydisposals.service.dms.PdfGenerationService
import uk.gov.hmrc.cgtpropertydisposals.util.{FileIOExecutionContext, Logging}
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import java.time.format.DateTimeFormatter
import java.time.{Clock, LocalDateTime}
import java.util.Base64
import scala.concurrent.Future

import play.api.libs.ws.WSBodyWritables.bodyWritableOf_Multipart

@Singleton
class DmsConnector @Inject() (
  httpClient: HttpClientV2,
  servicesConfig: ServicesConfig,
  pdfGeneratorService: PdfGenerationService,
  clock: Clock
)(implicit
  ex: FileIOExecutionContext
) extends Logging {
  private val cgtPropertyDisposalsUrl = servicesConfig.baseUrl("cgt-property-disposals")
  private val url                     = s"${servicesConfig.baseUrl("dms")}/dms-submission/submit"

  def submitToDms(
    dmsSubmissionPayload: DmsSubmissionPayload
  )(implicit hc: HeaderCarrier): Future[DmsEnvelopeId] = {
    val dataParts: Seq[MultipartFormData.DataPart] = Seq(
      MultipartFormData.DataPart("callbackUrl", s"$cgtPropertyDisposalsUrl/cgt-property-disposals/dms/callback"),
      MultipartFormData.DataPart("metadata.source", "cgtpd"),
      MultipartFormData
        .DataPart("metadata.timeOfReceipt", DateTimeFormatter.ISO_DATE_TIME.format(LocalDateTime.now(clock))),
      MultipartFormData.DataPart("metadata.formId", dmsSubmissionPayload.dmsMetadata.dmsFormId),
      MultipartFormData.DataPart("metadata.customerId", dmsSubmissionPayload.dmsMetadata.customerId),
      MultipartFormData.DataPart("metadata.classificationType", dmsSubmissionPayload.dmsMetadata.classificationType),
      MultipartFormData.DataPart("metadata.businessArea", dmsSubmissionPayload.dmsMetadata.businessArea)
    )

    val pdfBytes = pdfGeneratorService.generatePDFBytes(decode(dmsSubmissionPayload.b64Html.value))

    val fileParts: Seq[MultipartFormData.FilePart[Source[ByteString, ?]]] =
      Seq(
        MultipartFormData.FilePart(
          key = "form",
          filename = "form.pdf",
          contentType = Some("application/octet-stream"),
          ref = Source.single(ByteString(pdfBytes))
        )
      )

    val attachmentParts: Seq[MultipartFormData.FilePart[Source[ByteString, ?]]] = dmsSubmissionPayload.attachments.map {
      attachment =>
        MultipartFormData.FilePart(
          key = "attachment",
          filename = attachment.filename,
          contentType = Some("application/octet-stream"),
          ref = Source.apply(attachment.data)
        )
    }

    val source: Source[MultipartFormData.Part[Source[ByteString, ?]], NotUsed] = Source(
      dataParts ++ fileParts ++ attachmentParts
    )

    logger.info("Sending payload to dms service...")
    httpClient
      .post(url"$url")
      .setHeader(AUTHORIZATION -> servicesConfig.getString("internal-auth.token"))
      .withBody(source)
      .execute[DmsEnvelopeId]
  }

  private val decode = (s: String) => new String(Base64.getDecoder.decode(s))
}
