/*
 * Copyright 2019 HM Revenue & Customs
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
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, Enrolments, Error, KeyValuePair, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[TaxEnrolmentConnectorImpl])
trait TaxEnrolmentConnector {
  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class TaxEnrolmentConnectorImpl @Inject()(http: HttpClient, servicesConfig: ServicesConfig)(
  implicit ec: ExecutionContext
) extends TaxEnrolmentConnector
    with Logging {

  val serviceUrl: String = servicesConfig.baseUrl("tax-enrolments")

  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {

    val serviceName: String  = "HMRC-CGT-PD"
    val enrolmentUrl: String = s"$serviceUrl/tax-enrolments/service/$serviceName/enrolment"

    val enrolmentRequest = taxEnrolmentRequest.address match {
      case Address.UkAddress(line1, line2, line3, line4, postcode) =>
        Enrolments(
          List(KeyValuePair("Postcode", postcode)),
          List(KeyValuePair("CGTPDRef", taxEnrolmentRequest.cgtReference))
        )
      case Address.NonUkAddress(line1, line2, line3, line4, maybePostcode, countryCode) =>
        Enrolments(
          List(KeyValuePair("CountryCode", countryCode.code)),
          List(KeyValuePair("CGTPDRef", taxEnrolmentRequest.cgtReference))
        )
    }

    logger.info(
      s"Allocating enrolment with these details : $taxEnrolmentRequest"
    )
    EitherT[Future, Error, HttpResponse](
      http
        .PUT[Enrolments, HttpResponse](enrolmentUrl, enrolmentRequest)
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }
}
