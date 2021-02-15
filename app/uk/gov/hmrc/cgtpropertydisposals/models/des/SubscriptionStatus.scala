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

import play.api.libs.json.{JsError, JsString, JsSuccess, Reads}

sealed trait SubscriptionStatus extends Product with Serializable

object SubscriptionStatus {

  case object NotSubscribed extends SubscriptionStatus

  case object Subscribed extends SubscriptionStatus

  case object RegistrationFormReceived extends SubscriptionStatus

  case object SentToDs extends SubscriptionStatus

  case object DsOutcomeInProgress extends SubscriptionStatus

  case object Rejected extends SubscriptionStatus

  case object InProcessing extends SubscriptionStatus

  case object CreateFailed extends SubscriptionStatus

  case object Withdrawal extends SubscriptionStatus

  case object SentToRcm extends SubscriptionStatus

  case object ApprovedWithConditions extends SubscriptionStatus

  case object Revoked extends SubscriptionStatus

  case object Deregistered extends SubscriptionStatus

  case object ContractObjectInactive extends SubscriptionStatus

  implicit val reads: Reads[SubscriptionStatus] =
    Reads {
      case JsString("NO_FORM_BUNDLE_FOUND")     => JsSuccess(NotSubscribed)
      case JsString("SUCCESSFUL")               => JsSuccess(Subscribed)
      case JsString("REG_FORM_RECEIVED")        => JsSuccess(RegistrationFormReceived)
      case JsString("SENT_TO_DS")               => JsSuccess(SentToDs)
      case JsString("DS_OUTCOME_IN_PROGRESS")   => JsSuccess(DsOutcomeInProgress)
      case JsString("REJECTED")                 => JsSuccess(Rejected)
      case JsString("IN_PROCESSING")            => JsSuccess(InProcessing)
      case JsString("CREATE_FAILED")            => JsSuccess(CreateFailed)
      case JsString("WITHDRAWAL")               => JsSuccess(Withdrawal)
      case JsString("SENT_TO_RCM")              => JsSuccess(SentToRcm)
      case JsString("APPROVED_WITH_CONDITIONS") => JsSuccess(ApprovedWithConditions)
      case JsString("REVOKED")                  => JsSuccess(Revoked)
      case JsString("DE-REGISTERED")            => JsSuccess(Deregistered)
      case JsString("CONTRACT_OBJECT_INACTIVE") => JsSuccess(ContractObjectInactive)
      case other                                => JsError(s"Expected JsString for SubscriptionStatus but got $other")
    }

}
