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

package uk.gov.hmrc.cgtpropertydisposals.models.bpr

import julienrf.json.derived
import play.api.libs.json.OFormat
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{NINO, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.models.name.IndividualName

sealed trait BusinessPartnerRecordRequest extends Product with Serializable

object BusinessPartnerRecordRequest {

  final case class IndividualBusinessPartnerRecordRequest(
    id: Either[SAUTR, NINO],
    nameMatch: Option[IndividualName]
  ) extends BusinessPartnerRecordRequest

  final case class TrustBusinessPartnerRecordRequest(id: SAUTR) extends BusinessPartnerRecordRequest

  @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
  implicit val format: OFormat[BusinessPartnerRecordRequest] = derived.oformat[BusinessPartnerRecordRequest]

  implicit class BusinessPartnerRecordRequestOps(val r: BusinessPartnerRecordRequest) extends AnyVal {

    def fold[A](
      ifTrust: TrustBusinessPartnerRecordRequest => A,
      ifIndividual: IndividualBusinessPartnerRecordRequest => A): A = r match {
      case t: TrustBusinessPartnerRecordRequest      => ifTrust(t)
      case i: IndividualBusinessPartnerRecordRequest => ifIndividual(i)
    }

    def id: Either[SAUTR, NINO] = fold(t => Left(t.id), _.id)

  }

}