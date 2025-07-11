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

package uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.Json
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Postcode}
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments._
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import scala.concurrent.{ExecutionContext, Future}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

@ImplementedBy(classOf[TaxEnrolmentConnectorImpl])
trait TaxEnrolmentConnector {
  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

  def updateVerifiers(updateVerifiersRequest: UpdateVerifiersRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class TaxEnrolmentConnectorImpl @Inject() (http: HttpClientV2, servicesConfig: ServicesConfig)(implicit
  ec: ExecutionContext
) extends TaxEnrolmentConnector
    with Logging {
  private val serviceUrl = servicesConfig.baseUrl("tax-enrolments")

  def updateVerifiers(updateVerifierDetails: UpdateVerifiersRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val updateVerifiersUrl: String =
      s"$serviceUrl/tax-enrolments/enrolments/HMRC-CGT-PD~CGTPDRef~${updateVerifierDetails.subscribedUpdateDetails.newDetails.cgtReference.value}"

    logger.info(
      s"Updating verifiers for cgt account ${updateVerifierDetails.subscribedUpdateDetails.newDetails.cgtReference}"
    )

    val body = TaxEnrolmentUpdateRequest(
      List(Address.toVerifierFormat(updateVerifierDetails.subscribedUpdateDetails.newDetails.address)),
      Legacy(
        List(Address.toVerifierFormat(updateVerifierDetails.subscribedUpdateDetails.previousDetails.address))
      )
    )

    EitherT[Future, Error, HttpResponse](
      http
        .put(url"$updateVerifiersUrl")
        .withBody(Json.toJson(body))
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e =>
          Left(Error(e))
        }
    )
  }

  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val serviceName: String  = "HMRC-CGT-PD"
    val enrolmentUrl: String = s"$serviceUrl/tax-enrolments/service/$serviceName/enrolment"

    val enrolmentRequest = taxEnrolmentRequest.address match {
      case Address.UkAddress(_, _, _, _, postcode)          =>
        Enrolments(
          List(KeyValuePair("Postcode", Postcode(postcode.value).value)),
          List(KeyValuePair("CGTPDRef", taxEnrolmentRequest.cgtReference))
        )
      case Address.NonUkAddress(_, _, _, _, _, countryCode) =>
        Enrolments(
          List(KeyValuePair("CountryCode", countryCode.code)),
          List(KeyValuePair("CGTPDRef", taxEnrolmentRequest.cgtReference))
        )
    }

    EitherT[Future, Error, HttpResponse](
      http
        .put(url"$enrolmentUrl")
        .withBody(Json.toJson(enrolmentRequest))
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e => Left(Error(e)) }
    )
  }
}
