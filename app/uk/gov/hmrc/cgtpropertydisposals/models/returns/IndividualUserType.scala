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

package uk.gov.hmrc.cgtpropertydisposals.models.returns

import play.api.libs.json.*

sealed trait IndividualUserType extends Product with Serializable

object IndividualUserType {

  case object Self extends IndividualUserType

  case object Capacitor extends IndividualUserType

  case object PersonalRepresentative extends IndividualUserType

  case object PersonalRepresentativeInPeriodOfAdmin extends IndividualUserType

  implicit val individualUserTypeFormat: Format[IndividualUserType] = new Format[IndividualUserType] {
    override def reads(json: JsValue): JsResult[IndividualUserType] = json match {
      case JsObject(fields) if fields.size == 1 =>
        fields.head._1 match {
          case "Self"                                  => JsSuccess(Self)
          case "Capacitor"                             => JsSuccess(Capacitor)
          case "PersonalRepresentative"                => JsSuccess(PersonalRepresentative)
          case "PersonalRepresentativeInPeriodOfAdmin" => JsSuccess(PersonalRepresentativeInPeriodOfAdmin)
          case other                                   => JsError(s"Invalid individual user type: $other")
        }
      case _                                    => JsError("Expected JSON object with one IndividualUserType key")
    }

    override def writes(o: IndividualUserType): JsValue = o match {
      case Self                                  => Json.obj("Self" -> Json.obj())
      case Capacitor                             => Json.obj("Capacitor" -> Json.obj())
      case PersonalRepresentative                => Json.obj("PersonalRepresentative" -> Json.obj())
      case PersonalRepresentativeInPeriodOfAdmin => Json.obj("PersonalRepresentativeInPeriodOfAdmin" -> Json.obj())
    }
  }

}
