/*
 * Copyright 2020 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.upscan

import cats.Eq
import julienrf.json.derived
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanStatus.{FAILED, READY}

sealed trait UpscanStatus
object UpscanStatus {
  case object READY extends UpscanStatus
  case object FAILED extends UpscanStatus

  implicit val eq: Eq[UpscanStatus]          = Eq.fromUniversalEquals
  implicit val format: OFormat[UpscanStatus] = derived.oformat()
}

final case class UpscanCallBack(
  cgtReference: CgtReference,
  reference: String,
  fileStatus: UpscanStatus,
  downloadUrl: Option[String],
  details: Map[String, String]
)

object UpscanCallBack {
  implicit val format: OFormat[UpscanCallBack] = derived.oformat()
}

final case class UpscanCallBackEvent(
  reference: String,
  fileStatus: String,
  downloadUrl: Option[String],
  uploadDetails: Option[Map[String, String]],
  failureDetails: Option[Map[String, String]]
)

object UpscanCallBackEvent {

  def toUpscanCallBack(cgtReference: CgtReference, upscanCallBackEvent: UpscanCallBackEvent): UpscanCallBack =
    UpscanCallBack(
      cgtReference = cgtReference,
      reference    = upscanCallBackEvent.reference,
      fileStatus   = convertFileStatus(upscanCallBackEvent.fileStatus),
      downloadUrl  = upscanCallBackEvent.downloadUrl,
      details = upscanCallBackEvent.uploadDetails.getOrElse(Map.empty) ++ upscanCallBackEvent.failureDetails.getOrElse(
        Map.empty
      )
    )

  def convertFileStatus(status: String): UpscanStatus = status match {
    case "READY"  => READY
    case "FAILED" => FAILED
  }

  implicit val format = Json.format[UpscanCallBackEvent]
}
