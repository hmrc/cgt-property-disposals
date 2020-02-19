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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.Configuration
import play.api.http.Status.OK
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse.CreateReturnSuccessful
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnResponse._
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.AuditService
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[DefaultCompleteReturnsService])
trait CompleteReturnsService {

  def createReturn(completeReturn: CompleteReturn)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubmitReturnResponse]

}

@Singleton
class DefaultCompleteReturnsService @Inject() (
  auditService: AuditService,
  submitReturnsConnector: SubmitReturnsConnector,
  emailConnector: EmailConnector,
  config: Configuration,
  metrics: Metrics
) extends CompleteReturnsService
    with Logging {

  override def createReturn(
    completeReturn: CompleteReturn
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubmitReturnResponse] =
    sendSubmitReturnRequest(completeReturn)

  private def sendSubmitReturnRequest(
    completeReturn: CompleteReturn
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubmitReturnResponse] =
    submitReturnsConnector.submit(completeReturn).subflatMap { response =>
      if (response.status === OK) {
        response.parseJSON[CreateReturnSuccessful]().leftMap(Error(_))
      } else {
        metrics.subscriptionCreateErrorCounter.inc()
        Left(Error(s"call to subscribe came back with status ${response.status}"))
      }
    }

  def prepareSubmitReturnResponse(successfulResponse: CreateReturnSuccessful): SubmitReturnResponse = {
    val createdReturnSuccessful = CreateReturnSuccessful(
      processingDate           = successfulResponse.processingDate,
      ppdReturnResponseDetails = successfulResponse.ppdReturnResponseDetails
    )
    createdReturnSuccessful
  }
}
