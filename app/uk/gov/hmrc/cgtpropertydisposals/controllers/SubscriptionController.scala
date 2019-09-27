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

package uk.gov.hmrc.cgtpropertydisposals.controllers

import akka.actor.ActorRef
import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.RetryTaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.controllers.SubscriptionController.SubscriptionError
import uk.gov.hmrc.cgtpropertydisposals.controllers.SubscriptionController.SubscriptionError.{BackendError, FailedTaxEnrolmentCall, RequestValidationError}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, SubscriptionDetails, SubscriptionResponse, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.modules.TaxEnrolmentRetryProvider
import uk.gov.hmrc.cgtpropertydisposals.service.{SubscriptionService, TaxEnrolmentService}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionController @Inject()(
  authenticate: AuthenticateActions,
  subscriptionService: SubscriptionService,
  taxEnrolmentService: TaxEnrolmentService,
  taxEnrolmentRetryProvider: TaxEnrolmentRetryProvider,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  @SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
  def subscribe(): Action[AnyContent] = authenticate.async { implicit request =>
    val result: EitherT[Future, SubscriptionError, SubscriptionResponse] =
      for {
        json <- EitherT.fromEither[Future](
                 Either.fromOption(request.body.asJson, RequestValidationError("No JSON body found in request"))
               )
        subscriptionDetails <- EitherT.fromEither[Future](
                                json
                                  .validate[SubscriptionDetails]
                                  .asEither
                                  .leftMap(
                                    e =>
                                      RequestValidationError(s"Could not parse JSON body as subscription request: $e")
                                  )
                              )
        subscriptionResponse <- subscriptionService
                                 .subscribe(subscriptionDetails)
                                 .leftMap[SubscriptionError](BackendError(_))
        _ <- taxEnrolmentService
              .allocateEnrolmentToGroup(
                TaxEnrolmentRequest(
                  request.user.id,
                  subscriptionResponse.cgtReferenceNumber,
                  subscriptionDetails.address,
                  timestamp = request.timestamp
                )
              )
              .leftMap[SubscriptionError](
                tt => FailedTaxEnrolmentCall(tt.taxEnrolmentRequest, subscriptionResponse.cgtReferenceNumber)
              )
      } yield subscriptionResponse

    result.fold(
      {
        case RequestValidationError(msg) =>
          logger.warn(s"Error in request to subscribe: $msg")
          BadRequest
        case BackendError(e) =>
          logger.warn("Error while trying to subscribe", e)
          InternalServerError
        case FailedTaxEnrolmentCall(taxEnrolmentRequest, cgt) =>
          logger.warn("Error while trying to allocate enrolment - sending message to retry asynchronously")
          retry(taxEnrolmentRequest).value.map {
            case Left(error) =>
              logger.warn(s"Failed to submit async retry request for $taxEnrolmentRequest: error [$error]")
            case Right(_) => logger.info(s"Successfully submitted async retry request for $taxEnrolmentRequest")
          }
          Ok(Json.toJson(SubscriptionResponse(cgt)))
      }, { r =>
        Ok(Json.toJson(r))
      }
    )
  }

  private def retry(taxEnrolmentRequest: TaxEnrolmentRequest): EitherT[Future, Error, Unit] =
    EitherT[Future, Error, Unit](
      Future {
        Right(
          taxEnrolmentRetryProvider.taxEnrolmentRetryManager
            .tell(
              RetryTaxEnrolmentRequest(taxEnrolmentRequest),
              ActorRef.noSender
            )
        )
      }.recover {
        case e => Left(Error(e.getMessage))
      }
    )
}

object SubscriptionController {

  sealed trait SubscriptionError

  object SubscriptionError {

    final case class BackendError(e: Error) extends SubscriptionError

    final case class RequestValidationError(msg: String) extends SubscriptionError

    final case class UserIdError(msg: String) extends SubscriptionError

    final case class FailedTaxEnrolmentCall(taxEnrolmentRequest: TaxEnrolmentRequest, cgtReference: String)
        extends SubscriptionError
  }

}
