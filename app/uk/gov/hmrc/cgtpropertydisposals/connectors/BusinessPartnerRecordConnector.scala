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

import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsObject, JsValue, Writes}
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models.NINO
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BusinessPartnerRecordConnectorImpl])
trait BusinessPartnerRecordConnector {

  def getBusinessPartnerRecord(nino: NINO)(implicit hc: HeaderCarrier): Future[HttpResponse]

}

@Singleton
class BusinessPartnerRecordConnectorImpl @Inject() (
    http: HttpClient,
    servicesConfig: ServicesConfig
)(implicit ec: ExecutionContext) extends BusinessPartnerRecordConnector {

  val baseUrl: String = servicesConfig.baseUrl("business-partner-record")

  val bearerToken: String = servicesConfig.getString("des.bearer-token")

  val environment: String = servicesConfig.getString("des.environment")

  val headers: Map[String, String] = Map(
    "Authorization" -> s"Bearer $bearerToken",
    "Environment" -> environment
  )

  val body: JsValue = JsObject(Map.empty[String, JsValue])

  def url(nino: NINO): String = s"$baseUrl/registration/individual/nino/${nino.value}"

  def getBusinessPartnerRecord(nino: NINO)(implicit hc: HeaderCarrier): Future[HttpResponse] =
    http.post(url(nino), body, headers)(implicitly[Writes[JsValue]], hc.copy(authorization = None), ec)

}
