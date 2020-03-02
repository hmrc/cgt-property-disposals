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

package uk.gov.hmrc.cgtpropertydisposals.models.des.returns

import play.api.libs.json._
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails

sealed trait CustomerType extends Product with Serializable

object CustomerType {

  case object Trust extends CustomerType

  case object Individual extends CustomerType

  def apply(subscribedDetails: SubscribedDetails): CustomerType =
    subscribedDetails.name.fold(_ => Trust, _ => Individual)

  implicit val format: Format[CustomerType] = Format(
    { json: JsValue =>
      json match {
        case JsString("trust")      => JsSuccess(Trust)
        case JsString("individual") => JsSuccess(Individual)
        case JsString(other)        => JsError(s"Could not parse customer type: $other")
        case other                  => JsError(s"Expected string for customer type but got $other")
      }
    }, { customerType: CustomerType =>
      customerType match {
        case Trust      => JsString("trust")
        case Individual => JsString("individual")
      }
    }
  )

}
