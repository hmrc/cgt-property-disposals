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

import java.time.LocalDate

import cats.data.EitherT
import cats.syntax.either._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{JsValue, Json, OFormat, Writes}
import uk.gov.hmrc.cgtpropertydisposals.http.HttpClient._
import uk.gov.hmrc.cgtpropertydisposals.models._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClient

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BusinessPartnerRecordConnectorImpl])
trait BusinessPartnerRecordConnector {

  def getBusinessPartnerRecord(bprRequest: BprRequest)(
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

  import BusinessPartnerRecordConnectorImpl._

  val baseUrl: String = config.baseUrl("business-partner-record")

  def url(id: Either[SAUTR,NINO]): String = {
    val suffix = id.fold(s => s"/sautr/${s.value}", n => s"/nino/${n.value}")
    s"$baseUrl/registration/individual$suffix"
  }


  def getBusinessPartnerRecord(bprRequest: BprRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, HttpResponse] = {
    val registerDetails = RegisterDetails(
      regime            = "HMRC-CGT-PD",
      requiresNameMatch = bprRequest.requiresNameMatch,
      isAnIndividual    = bprRequest.entity.isRight,
      individual        = bprRequest.entity.map(i =>
        RegisterIndividual(i.name.firstName, i.name.lastName, i.dateOfBirth.map(_.value))
      ).toOption
    )
    EitherT[Future, Error, HttpResponse](
      http
        .post(url(bprRequest.id), Json.toJson(registerDetails), headers)(
          implicitly[Writes[JsValue]],
          hc.copy(authorization = None),
          ec
        )
        .map(Right(_))
        .recover {
          case e => Left(Error(e, "id" -> bprRequest.id.fold(_.value, _.value)))
        }
    )
  }

}

object BusinessPartnerRecordConnectorImpl {

  private final case class RegisterDetails(regime: String,
                                           requiresNameMatch: Boolean,
                                           isAnIndividual: Boolean,
                                           individual: Option[RegisterIndividual]
                                          )

  private final case class RegisterIndividual(firstName: String, lastName: String, dateOfBirth: Option[LocalDate])

  private implicit val desIndividualFormat: OFormat[RegisterIndividual] = Json.format[RegisterIndividual]

  private implicit val registerDetailsFormat: OFormat[RegisterDetails] = Json.format[RegisterDetails]

}
