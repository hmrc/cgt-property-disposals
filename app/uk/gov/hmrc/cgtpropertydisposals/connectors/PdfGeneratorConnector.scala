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

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.mvc.Http.Status
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[PdfGeneratorConnectorImpl])
trait PdfGeneratorConnector {
  def generatePDF(payload: Map[String, Seq[String]], headers: Seq[(String, String)])(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Stream[Char]]
}

@Singleton
class PdfGeneratorConnectorImpl @Inject()(config: ServicesConfig, http: HttpClient)(implicit ec: ExecutionContext)
    extends PdfGeneratorConnector
    with Logging {

  private val serviceName: String  = "pdf-generator"
  private lazy val baseURL: String = config.baseUrl(serviceName) + config.getConfString(s"$serviceName.base-path", "")
  private val url: String          = s"$baseURL/pdf-generator-service/generate"

  override def generatePDF(payload: Map[String, Seq[String]], headers: Seq[(String, String)])(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Stream[Char]] =
    EitherT[Future, Error, Stream[Char]](
      http
        .POSTForm(url, payload, headers)
        .map[Either[Error, Stream[Char]]] { response =>
          if (response.status != Status.OK) {
            logger.warn(s"Could not generate PDF: http status : ${response.status} | http body : ${response.body}")
            Left(Error("Could not generate PDF"))
          } else {
            Right(response.body.toStream)
          }
        }
        .recover { case e => Left(Error(e)) }
    )
}
