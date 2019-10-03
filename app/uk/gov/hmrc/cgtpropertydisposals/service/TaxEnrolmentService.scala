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

import akka.pattern.ask
import akka.util.Timeout
import cats.data.EitherT
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager._
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.modules.TaxEnrolmentRetryProvider
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

@ImplementedBy(classOf[TaxEnrolmentServiceImpl])
trait TaxEnrolmentService {
  def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit]
}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(
  config: Configuration,
  taxEnrolmentRetryProvider: TaxEnrolmentRetryProvider
) extends TaxEnrolmentService
    with Logging {

  override def allocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, Unit] = {

    val akkaAskTimeout: FiniteDuration =
      config.underlying.get[FiniteDuration]("tax-enrolment-retry.akka-ask-timeout").value

    implicit val timeout: Timeout = Timeout(akkaAskTimeout)

    EitherT[Future, Error, Unit](
      taxEnrolmentRetryProvider.taxEnrolmentRetryManager
        .ask(QueueTaxEnrolmentRequest(taxEnrolmentRequest))
        .mapTo[QueueTaxEnrolmentResponse]
        .map[Either[Error, Unit]] {
          case QueueTaxEnrolmentRequestFailure => {
            Left(Error(s"Failed to queue tax enrolment request $taxEnrolmentRequest"))
          }
          case QueueTaxEnrolmentRequestSuccess => {
            logger.info(s"Successfully queued tax enrolment request $taxEnrolmentRequest")
            Right(())
          }
        }
        .recover {
          case e: Exception => {
            Left(
              Error(
                s"Received an exception when sending message to call tax enrolment service $e"
              )
            )
          }
        }
    )
  }
}
