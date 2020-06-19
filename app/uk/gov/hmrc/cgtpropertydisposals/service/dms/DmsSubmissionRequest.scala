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

package uk.gov.hmrc.cgtpropertydisposals.service.dms

import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.B64Html
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn
import uk.gov.hmrc.http.logging._
import uk.gov.hmrc.http.{HeaderCarrier, Token, UserId}
import uk.gov.hmrc.workitem.WorkItem

final case class DmsSubmissionRequest(
  headerCarrier: HeaderCarrier,
  html: B64Html,
  formBundleId: String,
  cgtReference: CgtReference,
  completeReturn: CompleteReturn
)

object DmsSubmissionRequest {
  implicit val authorizationFormat: OFormat[Authorization]               = Json.format
  implicit val userIdFOrmat: OFormat[UserId]                             = Json.format
  implicit val tokenFormat: OFormat[Token]                               = Json.format
  implicit val forwardedForFormat: OFormat[ForwardedFor]                 = Json.format
  implicit val sessionIdFormat: OFormat[SessionId]                       = Json.format
  implicit val requestIdFormat: OFormat[RequestId]                       = Json.format
  implicit val requestChainFormat: OFormat[RequestChain]                 = Json.format
  implicit val akamaiReputationFormat: OFormat[AkamaiReputation]         = Json.format
  implicit val headerCarrierFormat: OFormat[HeaderCarrier]               = Json.format
  implicit val dmsSubmissionRequestFormat: OFormat[DmsSubmissionRequest] = Json.format
  val workItemFormat: Format[WorkItem[DmsSubmissionRequest]]             = WorkItem.workItemMongoFormat[DmsSubmissionRequest]
}
