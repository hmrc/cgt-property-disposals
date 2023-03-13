/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service.audit

import com.google.inject.ImplementedBy
import play.api.libs.json.{Json, Writes}
import play.api.mvc.Request
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import javax.inject.Inject
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def sendEvent[A](auditType: String, detail: A, transactionName: String)(implicit
    hc: HeaderCarrier,
    writes: Writes[A],
    request: Request[_]
  ): Unit

}

class AuditServiceImpl @Inject() (
  auditConnector: AuditConnector
)(implicit ec: ExecutionContext)
    extends AuditService
    with Logging {

  override def sendEvent[A](auditType: String, detail: A, transactionName: String)(implicit
    hc: HeaderCarrier,
    writes: Writes[A],
    request: Request[_]
  ): Unit = {
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = "cgt-property-disposals",
      auditType = auditType,
      detail = Json.toJson(detail),
      tags = hc.toAuditTags(transactionName, request.uri)
    )
    val _                 = auditConnector.sendExtendedEvent(extendedDataEvent)
  }

}
