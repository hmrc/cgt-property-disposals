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

package uk.gov.hmrc.cgtpropertydisposals.service

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.libs.json.{JsValue, Json}
import uk.gov.hmrc.cgtpropertydisposals.models.audit.{RegistrationResponseEvent, SubscriptionResponseEvent}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent
import cats.instances.string._
import cats.Eq._
import scala.concurrent.ExecutionContext

@ImplementedBy(classOf[AuditServiceImpl])
trait AuditService {

  def sendSubscriptionResponse(httpStatus: Int, body: String, path: String)(
    implicit hc: HeaderCarrier,
    ec: ExecutionContext
  ): Unit

  def sendRegistrationResponse(
    httpStatus: Int,
    body: String,
    path: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Unit

}

class AuditServiceImpl @Inject()(
  auditConnector: AuditConnector
) extends AuditService {

  private def sendEvent(auditSource: String, auditType: String, detail: JsValue, tags: Map[String, String])(
    implicit ec: ExecutionContext
  ): Unit = {
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = auditSource,
      auditType   = auditType,
      detail      = detail,
      tags        = tags
    )
    auditConnector.sendExtendedEvent(extendedDataEvent)
  }

  override def sendSubscriptionResponse(
    httpStatus: Int,
    body: String,
    path: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Unit = {

    val detail = SubscriptionResponseEvent(
      httpStatus,
      body
    )

    sendEvent(
      "cgt-property-disposals",
      "subscriptionResponse",
      Json.toJson(detail),
      hc.toAuditTags("subscription-response", path)
    )

  }

  override def sendRegistrationResponse(
    httpStatus: Int,
    body: String,
    path: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Unit = {

    val detail = RegistrationResponseEvent(
      httpStatus,
      body
    )

    sendEvent(
      "cgt-property-disposals",
      "registrationResponse",
      Json.toJson(detail),
      hc.toAuditTags("registration-response", path)
    )
  }

}
