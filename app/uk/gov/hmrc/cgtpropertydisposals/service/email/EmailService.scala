/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service.email

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.mvc.Request
import play.api.http.Status.ACCEPTED
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.ContactName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.SubscriptionConfirmationEmailSentEvent
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.audit.ReturnConfirmationEmailSentEvent
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[EmailServiceImpl])
trait EmailService {

  def sendSubscriptionConfirmationEmail(
    cgtReference: CgtReference,
    email: Email,
    contactName: ContactName
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit]

  def sendReturnConfirmationEmail(
    returnRequest: SubmitReturnRequest,
    submitReturnResponse: SubmitReturnResponse
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit]

}

@Singleton
class EmailServiceImpl @Inject() (connector: EmailConnector, auditService: AuditService, metrics: Metrics)(implicit
  ec: ExecutionContext
) extends EmailService
    with Logging {

  def sendSubscriptionConfirmationEmail(
    cgtReference: CgtReference,
    email: Email,
    contactName: ContactName
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit] = {
    val timer = metrics.subscriptionConfirmationEmailTimer.time()

    connector
      .sendSubscriptionConfirmationEmail(cgtReference, email, contactName)
      .subflatMap { httpResponse =>
        timer.close()

        if (httpResponse.status =!= ACCEPTED) {
          metrics.subscriptionConfirmationEmailErrorCounter.inc()
          Left(Error(s"Call to send confirmation email came back with status ${httpResponse.status}"))
        } else
          Right(
            auditSubscriptionConfirmationEmailSent(
              email.value,
              cgtReference.value
            )
          )
      }
  }

  def sendReturnConfirmationEmail(
    returnRequest: SubmitReturnRequest,
    submitReturnResponse: SubmitReturnResponse
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, Unit] = {
    val timer = metrics.submitReturnConfirmationEmailTimer.time()

    connector
      .sendReturnSubmitConfirmationEmail(
        submitReturnResponse,
        returnRequest.subscribedDetails
      )
      .subflatMap { httpResponse =>
        timer.close()
        if (httpResponse.status === ACCEPTED)
          Right(auditSubscriptionConfirmationEmailSent(returnRequest, submitReturnResponse))
        else {
          metrics.submitReturnConfirmationEmailErrorCounter.inc()
          Left(Error(s"Call to send confirmation email came back with status ${httpResponse.status}"))
        }
      }
  }

  private def auditSubscriptionConfirmationEmailSent(
    emailAddress: String,
    cgtReference: String
  )(implicit hc: HeaderCarrier, request: Request[_]): Unit =
    auditService.sendEvent(
      "subscriptionConfirmationEmailSent",
      SubscriptionConfirmationEmailSentEvent(
        emailAddress,
        cgtReference
      ),
      "subscription-confirmation-email-sent"
    )

  private def auditSubscriptionConfirmationEmailSent(
    returnRequest: SubmitReturnRequest,
    submitReturnResponse: SubmitReturnResponse
  )(implicit hc: HeaderCarrier, request: Request[_]): Unit =
    auditService.sendEvent(
      "returnConfirmationEmailSent",
      ReturnConfirmationEmailSentEvent(
        returnRequest.subscribedDetails.emailAddress.value,
        returnRequest.subscribedDetails.cgtReference.value,
        submitReturnResponse.formBundleId
      ),
      "return-confirmation-email-sent"
    )

}
