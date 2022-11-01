/*
 * Copyright 2022 HM Revenue & Customs
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

import play.api.libs.functional.syntax.toFunctionalBuilderOps
import play.api.libs.json.Format.GenericFormat

import java.time.LocalDateTime
import play.api.libs.json.{JsPath, Json, OFormat, Reads}

final case class UpscanUpload(
  uploadReference: UploadReference,
  upscanUploadMeta: UpscanUploadMeta,
  uploadedOn: LocalDateTime,
  upscanCallBack: Option[UpscanCallBack]
)

object UpscanUpload {
  implicit val format: OFormat[UpscanUpload] = Json.format[UpscanUpload]

  implicit val reads: Reads[UpscanUpload] = (
    (JsPath \ "uploadReference").read[UploadReference] and
      (JsPath \ "upscanUploadMeta").read[UpscanUploadMeta] and
      (JsPath \ "uploadedOn").read[LocalDateTime] and
      (JsPath \ "upscanCallBack").readNullable[UpscanCallBack]
  )(UpscanUpload.apply _)
}
