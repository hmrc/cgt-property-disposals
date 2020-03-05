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

import java.time.LocalDateTime

import cats.Eq
import julienrf.json.derived
import play.api.libs.json.{Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.UpscanFileDescriptor.UpscanFileDescriptorStatus

final case class UpscanFileDescriptor(
  key: String,
  cgtReference: CgtReference,
  fileDescriptor: FileDescriptor,
  timestamp: LocalDateTime,
  status: UpscanFileDescriptorStatus
)

object UpscanFileDescriptor {
  sealed trait UpscanFileDescriptorStatus
  object UpscanFileDescriptorStatus {
    case object FAILED extends UpscanFileDescriptorStatus
    case object UPLOADED extends UpscanFileDescriptorStatus
    case object READY_TO_UPLOAD extends UpscanFileDescriptorStatus
    case object READY extends UpscanFileDescriptorStatus
    case object QUARANTINE extends UpscanFileDescriptorStatus
    case object REJECTED extends UpscanFileDescriptorStatus
    case object UNKNOWN extends UpscanFileDescriptorStatus
  }
  implicit val eq: Eq[UpscanFileDescriptorStatus]                = Eq.fromUniversalEquals
  implicit val statusFormat: OFormat[UpscanFileDescriptorStatus] = derived.oformat()
  implicit val format: OFormat[UpscanFileDescriptor]             = Json.format[UpscanFileDescriptor]

}