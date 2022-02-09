/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpClient, HttpResponse}
import uk.gov.hmrc.http.HttpReads.Implicits.readRaw
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EnrolmentStoreProxyConnectorImpl])
trait EnrolmentStoreProxyConnector {

  def getPrincipalEnrolments(cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class EnrolmentStoreProxyConnectorImpl @Inject() (http: HttpClient, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) extends EnrolmentStoreProxyConnector
    with Logging {

  private val serviceUrl: String = servicesConfig.baseUrl("enrolment-store-proxy")

  private def getPrincipalEnrolmentsUrl(cgtReference: CgtReference): String =
    s"$serviceUrl/enrolment-store-proxy/enrolment-store/enrolments/HMRC-CGT-PD~CGTPDRef~${cgtReference.value}/users"

  private val principalEnrolmentsQueryParameters = List("type" -> "principal")

  def getPrincipalEnrolments(
    cgtReference: CgtReference
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, HttpResponse] =
    EitherT[Future, Error, HttpResponse](
      http
        .GET[HttpResponse](
          getPrincipalEnrolmentsUrl(cgtReference),
          principalEnrolmentsQueryParameters,
          Seq.empty
        )
        .map(Right(_))
        .recover { case e =>
          Left(Error(e))
        }
    )

}
