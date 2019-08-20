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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import play.api.libs.json._

final case class BprRequest(nino: String, forename: String, surname: String, dateOfBirth: LocalDate)

object BprRequest {
//  implicit val localDateFormat: Format[LocalDate] = new Format[LocalDate] {
//    override def reads(json: JsValue): JsResult[LocalDate] =
//      json.validate[String].map(date => LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE))
//
//    override def writes(o: LocalDate): JsValue = Json.toJson(o.toString)
//  }
  implicit val format: OFormat[BprRequest] = Json.format[BprRequest]
}
