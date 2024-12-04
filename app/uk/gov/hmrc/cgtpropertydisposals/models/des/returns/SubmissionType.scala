/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json._

sealed trait SubmissionType extends Product with Serializable

object SubmissionType {

  final case object New extends SubmissionType

  final case object Amend extends SubmissionType

  implicit val format: Format[SubmissionType] = new Format[SubmissionType] {
    override def reads(json: JsValue): JsResult[SubmissionType] =
      json match {
        case JsString("New")   => JsSuccess(New)
        case JsString("Amend") => JsSuccess(Amend)
        case JsString(other)   => JsError(s"could not recognise submission type: $other")
        case other             => JsError(s"Expected type string for submission type but found $other")
      }

    override def writes(o: SubmissionType): JsValue =
      o match {
        case New   => JsString("New")
        case Amend => JsString("Amend")
      }
  }
}
