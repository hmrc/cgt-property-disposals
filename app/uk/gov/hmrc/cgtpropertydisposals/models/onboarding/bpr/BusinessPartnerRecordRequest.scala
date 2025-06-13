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

package uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr

import play.api.libs.json.{Format, JsDefined, JsError, JsObject, JsResult, JsValue, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{NINO, SAUTR, TRN}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}

object BusinessPartnerRecordRequest {

  final case class IndividualBusinessPartnerRecordRequest(
    id: Either[SAUTR, NINO],
    nameMatch: Option[IndividualName],
    ggCredId: String,
    createNewEnrolmentIfMissing: Boolean
  ) extends BusinessPartnerRecordRequest

  final case class TrustBusinessPartnerRecordRequest(
    id: Either[TRN, SAUTR],
    nameMatch: Option[TrustName],
    ggCredId: String,
    createNewEnrolmentIfMissing: Boolean
  ) extends BusinessPartnerRecordRequest

  implicit val sautrNinoFormat: Format[Either[SAUTR, NINO]] =
    EitherFormat.eitherFormat[SAUTR, NINO]
  implicit val trnSautrFormat: Format[Either[TRN, SAUTR]]   =
    EitherFormat.eitherFormat[TRN, SAUTR]

  implicit val trustFormat: OFormat[TrustBusinessPartnerRecordRequest]           = Json.format[TrustBusinessPartnerRecordRequest]
  implicit val individualFormat: OFormat[IndividualBusinessPartnerRecordRequest] =
    Json.format[IndividualBusinessPartnerRecordRequest]
  implicit val format: OFormat[BusinessPartnerRecordRequest]                     = new OFormat[BusinessPartnerRecordRequest] {
    override def reads(json: JsValue): JsResult[BusinessPartnerRecordRequest] =
      (json \ "IndividualBusinessPartnerRecordRequest", json \ "TrustBusinessPartnerRecordRequest") match {
        case (JsDefined(individualJson), _) => individualJson.validate[IndividualBusinessPartnerRecordRequest]
        case (_, JsDefined(trustJson))      => trustJson.validate[TrustBusinessPartnerRecordRequest]
        case _                              => JsError("Could not determine BusinessPartnerRecordRequest subtype from JSON")
      }

    override def writes(bpr: BusinessPartnerRecordRequest): JsObject = bpr match {
      case i: IndividualBusinessPartnerRecordRequest =>
        Json.obj("IndividualBusinessPartnerRecordRequest" -> Json.toJson(i)(individualFormat))
      case t: TrustBusinessPartnerRecordRequest      =>
        Json.obj("TrustBusinessPartnerRecordRequest" -> Json.toJson(t)(trustFormat))
    }
  }

  implicit class BusinessPartnerRecordRequestOps(private val r: BusinessPartnerRecordRequest) extends AnyVal {

    def fold[A](
      ifTrust: TrustBusinessPartnerRecordRequest => A,
      ifIndividual: IndividualBusinessPartnerRecordRequest => A
    ): A =
      r match {
        case t: TrustBusinessPartnerRecordRequest      => ifTrust(t)
        case i: IndividualBusinessPartnerRecordRequest => ifIndividual(i)
      }

    def foldOnId[A](
      ifTrn: TRN => A,
      ifSautr: SAUTR => A,
      ifNino: NINO => A
    ): A =
      fold(_.id.fold(ifTrn, ifSautr), _.id.fold(ifSautr, ifNino))

  }

}

sealed trait BusinessPartnerRecordRequest extends Product with Serializable {
  val ggCredId: String
  val createNewEnrolmentIfMissing: Boolean
}
