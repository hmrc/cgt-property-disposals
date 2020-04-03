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

package uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit

import com.github.ghik.silencer.silent

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.des.TypeOfPersonDetails
import uk.gov.hmrc.cgtpropertydisposals.models.des.TypeOfPersonDetails.{Individual, Trustee}
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest.{ContactDetails, DesSubscriptionDetails, Identity}

final case class SubscriptionResponseEvent(
  responseHttpStatusCode: Int,
  responseHttpBody: JsValue,
  requestBody: DesSubscriptionRequest
)

object SubscriptionResponseEvent {

  @silent
  implicit val writes: Writes[SubscriptionResponseEvent] = {
    val desSubscriptionRequestWrites: Writes[DesSubscriptionRequest] = {
      implicit val typeOfPersonDetailsWrites: Writes[TypeOfPersonDetails] = Writes { t =>
        val (name, typeOfPerson) = t match {
          case i: Individual => s"${i.firstName} ${i.lastName}" -> i.typeOfPerson
          case t: Trustee    => t.organisationName              -> t.typeOfPerson
        }
        JsObject(Map("name" -> JsString(name), "typeOfPerson" -> JsString(typeOfPerson)))
      }

      implicit val identityWrites: Writes[Identity]                             = Json.writes[Identity]
      implicit val contactDetailsWrites: Writes[ContactDetails]                 = Json.writes[ContactDetails]
      implicit val desSubscriptionDetailsWrites: Writes[DesSubscriptionDetails] = Json.writes[DesSubscriptionDetails]
      Json.writes
    }

    Writes[SubscriptionResponseEvent](event =>
      JsObject(
        Map(
          "responseHttpStatusCode" -> JsNumber(event.responseHttpStatusCode),
          "responseHttpBody"       -> event.responseHttpBody,
          "requestBody"            -> desSubscriptionRequestWrites.writes(event.requestBody)
        )
      )
    )
  }

}
