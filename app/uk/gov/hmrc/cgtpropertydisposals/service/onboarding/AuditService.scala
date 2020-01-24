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

package uk.gov.hmrc.cgtpropertydisposals.service.onboarding

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.{RegistrationResponseEvent, SubscriptionConfirmationEmailSentEvent, SubscriptionResponseEvent}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.ExtendedDataEvent

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

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

  def sendSubscriptionConfirmationEmailSentEvent(
    emailAddress: String,
    cgtReference: String,
    path: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit

}

class AuditServiceImpl @Inject() (
  auditConnector: AuditConnector
) extends AuditService
    with Logging {

  private def sendEvent[A](auditType: String, detail: A, tags: Map[String, String])(
    implicit ec: ExecutionContext,
    writes: Writes[A]
  ): Unit = {
    val extendedDataEvent = ExtendedDataEvent(
      auditSource = "cgt-property-disposals",
      auditType   = auditType,
      detail      = Json.toJson(detail),
      tags        = tags
    )
    auditConnector.sendExtendedEvent(extendedDataEvent)
  }

  override def sendSubscriptionResponse(
    httpStatus: Int,
    body: String,
    path: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Unit =
    Try {
      Json.parse(body)
    } match {
      case Failure(exception) => {
        sendEvent(
          "subscriptionResponse",
          SubscriptionResponseEvent(
            httpStatus,
            Json.parse(s"""{"body" : could not parse body as JSON: $body""")
          ),
          hc.toAuditTags("subscription-response", path)
        )
      }
      case Success(subscriptionResponse) => {
        sendEvent(
          "subscriptionResponse",
          SubscriptionResponseEvent(
            httpStatus,
            subscriptionResponse
          ),
          hc.toAuditTags("subscription-response", path)
        )
      }
    }

  override def sendRegistrationResponse(
    httpStatus: Int,
    body: String,
    path: String
  )(implicit hc: HeaderCarrier, executionContext: ExecutionContext): Unit =
    Try {
      Json.parse(body)
    } match {
      case Failure(exception) => {
        sendEvent(
          "registrationResponse",
          RegistrationResponseEvent(
            httpStatus,
            Json.parse(s"""{"body" : could not parse body as JSON: $body""")
          ),
          hc.toAuditTags("registration-response", path)
        )

      }
      case Success(subscriptionResponse) => {
        sendEvent(
          "registrationResponse",
          RegistrationResponseEvent(
            httpStatus,
            subscriptionResponse
          ),
          hc.toAuditTags("registration-response", path)
        )
      }
    }

  override def sendSubscriptionConfirmationEmailSentEvent(
    emailAddress: String,
    cgtReference: String,
    path: String
  )(implicit hc: HeaderCarrier, ec: ExecutionContext): Unit = {

    val detail = SubscriptionConfirmationEmailSentEvent(
      emailAddress,
      cgtReference
    )

    sendEvent(
      "subscriptionConfirmationEmailSent",
      detail,
      hc.toAuditTags("subscription-confirmation-email-sent", path)
    )
  }

}
