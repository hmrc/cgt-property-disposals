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

package uk.gov.hmrc.cgtpropertydisposals.models

import java.time.LocalDateTime

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest.{TaxEnrolmentInProgress, TaxEnrolmentRequestStatus}

final case class TaxEnrolmentRequest(
  userId: String,
  requestId: Long,
  cgtReference: String,
  address: Address,
  status: TaxEnrolmentRequestStatus = TaxEnrolmentInProgress,
  timestamp: LocalDateTime          = LocalDateTime.now()
)

object TaxEnrolmentRequest {
  sealed trait TaxEnrolmentRequestStatus extends Product with Serializable
  case object TaxEnrolmentInProgress extends TaxEnrolmentRequestStatus
  case object TaxEnrolmentFailed extends TaxEnrolmentRequestStatus

  implicit val statusFormat: Format[TaxEnrolmentRequestStatus] = new Format[TaxEnrolmentRequestStatus] {
    override def writes(o: TaxEnrolmentRequestStatus): JsValue = o match {
      case TaxEnrolmentInProgress => JsString("TaxEnrolmentInProgress")
      case TaxEnrolmentFailed     => JsString("TaxEnrolmentFailed")
    }

    override def reads(json: JsValue): JsResult[TaxEnrolmentRequestStatus] =
      json match {
        case JsString("TaxEnrolmentInProgress") => JsSuccess(TaxEnrolmentInProgress)
        case JsString("TaxEnrolmentFailed")     => JsSuccess(TaxEnrolmentFailed)
        case JsString(err) =>
          JsError(s"only two valid statuses:, TaxEnrolmentInProgress, TaxEnrolmentFailed")
        case _ => JsError("Failure")
      }
  }
  implicit val format: OFormat[TaxEnrolmentRequest] = Json.format[TaxEnrolmentRequest]

}
