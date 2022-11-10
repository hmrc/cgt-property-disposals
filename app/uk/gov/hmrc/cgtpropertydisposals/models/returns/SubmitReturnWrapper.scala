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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.functional.syntax._
import play.api.libs.json.{Format, __}
import uk.gov.hmrc.mongo.play.json.formats.MongoJavatimeFormats

import java.time.Instant

final case class SubmitReturnWrapper(
  id: String,
  submitReturnRequest: SubmitReturnRequest,
  lastUpdated: Instant
)

object SubmitReturnWrapper {
  val format: Format[SubmitReturnWrapper] = {
    implicit val dtf: Format[Instant] = MongoJavatimeFormats.instantFormat
    ((__ \ "_id").format[String]
      ~ (__ \ "return").format[SubmitReturnRequest]
      ~ (__ \ "lastUpdated").format[Instant])(SubmitReturnWrapper.apply, unlift(SubmitReturnWrapper.unapply))
  }
}
