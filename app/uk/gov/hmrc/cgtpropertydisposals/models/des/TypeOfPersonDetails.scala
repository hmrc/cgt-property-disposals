/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.models.des

import play.api.libs.json.{Json, Writes}

sealed trait TypeOfPersonDetails extends Product with Serializable

object TypeOfPersonDetails {

  final case class Individual(firstName: String, lastName: String, typeOfPerson: String = "Individual")
      extends TypeOfPersonDetails

  final case class Trustee(organisationName: String, typeOfPerson: String = "Trustee") extends TypeOfPersonDetails

  val individualWrites: Writes[Individual] = Json.writes[Individual]

  val trusteeWrites: Writes[Trustee] = Json.writes[Trustee]

  implicit val writes: Writes[TypeOfPersonDetails] = Writes {
    case i: Individual => individualWrites.writes(i)
    case t: Trustee    => trusteeWrites.writes(t)
  }

}
