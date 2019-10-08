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

import cats.data.EitherT
import cats.instances.future._
import cats.syntax.either._
import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.SubscriptionController.SubscriptionError
import uk.gov.hmrc.cgtpropertydisposals.controllers.SubscriptionController.SubscriptionError.{BackendError, RequestValidationError}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, SubscriptionDetails, SubscriptionResponse, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.service.{SubscriptionService, TaxEnrolmentService}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionController @Inject()(
  authenticate: AuthenticateActions,
  subscriptionService: SubscriptionService,
  taxEnrolmentService: TaxEnrolmentService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

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
                  request.user.ggCredId,
                  subscriptionResponse.cgtReferenceNumber,
                  subscriptionDetails.address,
                  timestamp = request.timestamp
                )
              )
              .leftMap[SubscriptionError](BackendError(_))
      } yield subscriptionResponse

    result.fold(
      {
        case RequestValidationError(msg) =>
          logger.warn(s"Error in request to subscribe: $msg")
          BadRequest
        case BackendError(e) =>
          logger.warn("Error while trying to subscribe:", e)
          InternalServerError
      }, { r =>
        Ok(Json.toJson(r))
      }
    )
  }

  def checkIfCgtEnrolmentExists(): Action[AnyContent] = authenticate.async { implicit request =>
    taxEnrolmentService.hasCgtEnrolment(request.user.ggCredId).value.map {
      case Left(error) =>
        logger.warn(s"Error checking existence of enrolment request: $error")
        InternalServerError
      case Right(maybeEnrolmentRequest) =>
        maybeEnrolmentRequest match {
          case Some(_) => Ok // user has subscribed but enrolment has not succeeded
          case None    => NoContent // user has no enrolment request pending
        }
    }
  }
}

object SubscriptionController {

  sealed trait SubscriptionError

  object SubscriptionError {

    final case class BackendError(e: Error) extends SubscriptionError

    final case class RequestValidationError(msg: String) extends SubscriptionError
  }

}
