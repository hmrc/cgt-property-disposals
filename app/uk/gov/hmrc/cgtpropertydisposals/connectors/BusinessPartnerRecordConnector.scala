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
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BusinessPartnerRecordConnectorImpl])
trait BusinessPartnerRecordConnector {

  def getBusinessPartnerRecord(nino: NINO, name: Name, dob: DateOfBirth)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]

}

@Singleton
class BusinessPartnerRecordConnectorImpl @Inject()(
  http: HttpClient,
  val config: ServicesConfig
)(implicit ec: ExecutionContext)
    extends BusinessPartnerRecordConnector
    with DesConnector {

  val baseUrl: String = config.baseUrl("business-partner-record")

  def url(nino: NINO): String = s"$baseUrl/registration/individual/nino/${nino.value}"

  def getBusinessPartnerRecord(nino: NINO, name: Name, dob: DateOfBirth)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val registerDetails = RegisterDetails(
      regime            = "CGT",  // TODO: TBD
      requiresNameMatch = false,
      isAnIndividual    = true,
      individual        = Individual(name.firstName, name.lastName, dob.value)
    )
    EitherT[Future, Error, HttpResponse](
      http
        .post(url(nino), Json.toJson(registerDetails), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover {
          case e => Left(Error(e, "nino" -> nino.value))
        }
    )
  }

}
