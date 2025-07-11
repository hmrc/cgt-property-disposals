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

package uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding

import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.connectors.DesConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.BusinessPartnerRecordConnectorImpl.{RegisterDetails, RegisterIndividual, RegisterOrganisation}
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.BusinessPartnerRecordRequest
import uk.gov.hmrc.cgtpropertydisposals.util.StringOps._
import uk.gov.hmrc.http.HttpReads.Implicits._
import uk.gov.hmrc.http.client.HttpClientV2
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, StringContextOps}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BusinessPartnerRecordConnectorImpl])
trait BusinessPartnerRecordConnector {
  def getBusinessPartnerRecord(bprRequest: BusinessPartnerRecordRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse]
}

@Singleton
class BusinessPartnerRecordConnectorImpl @Inject() (
  http: HttpClientV2,
  val config: ServicesConfig
)(implicit ec: ExecutionContext)
    extends BusinessPartnerRecordConnector
    with DesConnector {
  private val baseUrl = config.baseUrl("business-partner-record")

  private def url(bprRequest: BusinessPartnerRecordRequest) = {
    val entityType = bprRequest.fold(_ => "organisation", _ => "individual")
    val suffix     = bprRequest.foldOnId(
      t => s"trn/${t.value.removeAllWhitespaces()}",
      s => s"utr/${s.value.removeAllWhitespaces()}",
      n => s"nino/${n.value.removeAllWhitespaces()}"
    )
    s"$baseUrl/registration/$entityType/$suffix"
  }

  def getBusinessPartnerRecord(bprRequest: BusinessPartnerRecordRequest)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val registerDetails = RegisterDetails(
      regime = "CGT",
      requiresNameMatch = bprRequest.fold(_.nameMatch.isDefined, _.nameMatch.isDefined),
      isAnAgent = false,
      individual = bprRequest.fold(
        _ => None,
        _.nameMatch.map(name => RegisterIndividual(name.firstName, name.lastName))
      ),
      organisation = bprRequest.fold(
        _.nameMatch.map(name => RegisterOrganisation(name.value, "Not Specified")),
        _ => None
      )
    )

    EitherT[Future, Error, HttpResponse](
      http
        .post(url"${url(bprRequest)}")
        .withBody(Json.toJson(registerDetails))
        .setHeader(headers*)
        .execute[HttpResponse]
        .map(Right(_))
        .recover { case e =>
          Left(Error(e, "id" -> bprRequest.foldOnId(_.value, _.value, _.value)))
        }
    )
  }
}

object BusinessPartnerRecordConnectorImpl {
  final case class RegisterDetails(
    regime: String,
    requiresNameMatch: Boolean,
    isAnAgent: Boolean,
    individual: Option[RegisterIndividual],
    organisation: Option[RegisterOrganisation]
  )

  final case class RegisterIndividual(firstName: String, lastName: String)

  final case class RegisterOrganisation(organisationName: String, organisationType: String)

  implicit val desIndividualFormat: OFormat[RegisterIndividual] = Json.format[RegisterIndividual]

  implicit val desOrganisationFormat: OFormat[RegisterOrganisation] = Json.format[RegisterOrganisation]

  implicit val registerDetailsFormat: OFormat[RegisterDetails] = Json.format[RegisterDetails]
}
