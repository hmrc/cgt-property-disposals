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

package uk.gov.hmrc.cgtpropertydisposals.actors

import akka.actor.{Actor, Props}
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryConnectorActor.RetryCallToTaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.{MarkTaxEnrolmentRetryRequestAsSuccess, RetryTaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

class TaxEnrolmentRetryConnectorActor(
  taxEnrolmentService: TaxEnrolmentService
) extends Actor
    with Logging {
  import context.dispatcher
  implicit val hc: HeaderCarrier = HeaderCarrier()

  override def receive: Receive = {
    case request: RetryCallToTaxEnrolmentService =>
      val sender_ = sender()
      taxEnrolmentService.allocateEnrolmentToGroup(request.taxEnrolmentRequest).value.map {
        case Left(retry) =>
          logger.warn(s"Tax enrolment call failed. Sending message to retry...")
          sender_ ! RetryTaxEnrolmentRequest(retry.taxEnrolmentRequest)
        case Right(response) =>
          response match {
            case TaxEnrolmentCreated =>
              logger.info(s"Successfully allocated enrolment with these details: ${request.taxEnrolmentRequest}")
              sender_ ! MarkTaxEnrolmentRetryRequestAsSuccess(request.taxEnrolmentRequest)
            case fail: TaxEnrolmentFailedForSomeOtherReason =>
              if (is5xx(fail.httpResponse.status)) {
                logger.warn(
                  s"Tax enrolment service call failed with status: ${fail.httpResponse.status} - retrying"
                )
                sender_ ! RetryTaxEnrolmentRequest(request.taxEnrolmentRequest)
              } else {
                logger.warn(
                  s"Tax enrolment service call failed with http status: ${fail.httpResponse.status} and will not retry"
                )
              }
          }
      }
  }

  private def is5xx(status: Int): Boolean = status >= 500 && status < 600
}

object TaxEnrolmentRetryConnectorActor {
  final case class RetryCallToTaxEnrolmentService(taxEnrolmentRequest: TaxEnrolmentRequest)
  def props(taxEnrolmentService: TaxEnrolmentService): Props =
    Props(new TaxEnrolmentRetryConnectorActor(taxEnrolmentService))
}
