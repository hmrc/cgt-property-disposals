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
import play.api.libs.json.{JsString, Json, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import uk.gov.hmrc.cgtpropertydisposals.controllers.SubscriptionController.SubscriptionError
import uk.gov.hmrc.cgtpropertydisposals.controllers.SubscriptionController.SubscriptionError.{BackendError, RequestValidationError}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.{AuthenticateActions, AuthenticatedRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.subscription.{SubscribedDetails, SubscriptionDetails, SubscriptionResponse, SubscriptionUpdateResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, RegistrationDetails, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.service.{RegisterWithoutIdService, SubscriptionService, TaxEnrolmentService}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionController @Inject()(
  authenticate: AuthenticateActions,
  subscriptionService: SubscriptionService,
  taxEnrolmentService: TaxEnrolmentService,
  registerWithoutIdService: RegisterWithoutIdService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def subscribe(): Action[AnyContent] = authenticate.async { implicit request =>
    val result: EitherT[Future, SubscriptionError, SubscriptionResponse] =
      for {
        subscriptionDetails <- EitherT.fromEither[Future](extractRequest[SubscriptionDetails](request))
        subscriptionResponse <- subscribeAndEnrol(subscriptionDetails)
                                 .leftMap[SubscriptionError](BackendError)
      } yield subscriptionResponse

    result.fold(
      {
        case RequestValidationError(msg) =>
          logger.warn(s"Error in request to subscribe: $msg")
          BadRequest
        case BackendError(e) =>
          logger.warn("Error while trying to subscribe:", e)
          InternalServerError
      }, {
        case successful: SubscriptionSuccessful => Ok(Json.toJson(successful))
        case AlreadySubscribed                  => Conflict
      }
    )
  }

  def registerWithoutIdAndSubscribe(): Action[AnyContent] = authenticate.async { implicit request =>
    val result = for {
      registrationDetails <- EitherT.fromEither[Future](extractRequest[RegistrationDetails](request))
      sapNumber <- registerWithoutIdService
                    .registerWithoutId(registrationDetails)
                    .leftMap(BackendError)
      subscriptionResponse <- subscribeAndEnrol(
                               SubscriptionDetails.fromRegistrationDetails(registrationDetails, sapNumber)
                             ).leftMap[SubscriptionError](BackendError)
    } yield subscriptionResponse

    result.fold(
      {
        case RequestValidationError(msg) =>
          logger.warn(s"Error in request to register without id and subscribe: $msg")
          BadRequest
        case BackendError(e) =>
          logger.warn("Error while trying to register without id and subscribe:", e)
          InternalServerError
      }, {
        case successful: SubscriptionSuccessful => Ok(Json.toJson(successful))
        case AlreadySubscribed                  => Conflict
      }
    )
  }

  def checkSubscriptionStatus(): Action[AnyContent] = authenticate.async { implicit request =>
    taxEnrolmentService.hasCgtSubscription(request.user.ggCredId).value.map {
      case Left(error) =>
        logger.warn(s"Error checking existence of enrolment request: $error")
        InternalServerError
      case Right(maybeEnrolmentRequest) =>
        maybeEnrolmentRequest match {
          case Some(enrolmentRequest) => Ok(Json.obj("value" -> JsString(enrolmentRequest.cgtReference)))
          case None                   => NoContent
        }
    }
  }

  def getSubscription(cgtReference: CgtReference): Action[AnyContent] = authenticate.async { implicit request =>
    subscriptionService.getSubscription(cgtReference).value.map {
      case Left(error) =>
        logger.warn(s"Error getting subscription details: $error")
        InternalServerError
      case Right(subscribedDetails) => Ok(Json.toJson(subscribedDetails))
    }
  }

  def updateSubscription: Action[AnyContent] = authenticate.async { implicit request =>
    val result: EitherT[Future, SubscriptionError, SubscriptionUpdateResponse] =
      for {
        subscribedDetails <- EitherT.fromEither[Future](extractRequest[SubscribedDetails](request))
        subscriptionResponse <- subscriptionService
                                 .updateSubscription(subscribedDetails)
                                 .leftMap[SubscriptionError](BackendError)
      } yield subscriptionResponse

    result.fold(
      {
        case RequestValidationError(msg) =>
          logger.warn(s"Error in request to update subscription: $msg")
          BadRequest
        case BackendError(e) =>
          logger.warn("Error while trying to update subscription:", e)
          InternalServerError
      }, { subscriptionUpdateResponse =>
        Ok(Json.toJson(subscriptionUpdateResponse))
      }
    )
  }

  private def extractRequest[R: Reads](request: Request[AnyContent]): Either[SubscriptionError, R] =
    Either
      .fromOption(request.body.asJson, RequestValidationError("No JSON body found in request"))
      .flatMap(
        _.validate[R].asEither
          .leftMap(
            e =>
              RequestValidationError(
                s"Could not parse JSON body as register and subscribe request: $e"
            )
          )
      )

  private def subscribeAndEnrol(
    subscriptionDetails: SubscriptionDetails
  )(implicit request: AuthenticatedRequest[_]): EitherT[Future, Error, SubscriptionResponse] =
    for {
      subscriptionResponse <- subscriptionService.subscribe(subscriptionDetails)
      _ <- subscriptionResponse match {
            case SubscriptionSuccessful(cgtReferenceNumber) =>
              taxEnrolmentService
                .allocateEnrolmentToGroup(
                  TaxEnrolmentRequest(
                    request.user.ggCredId,
                    cgtReferenceNumber,
                    subscriptionDetails.address,
                    timestamp = request.timestamp
                  )
                )

            case AlreadySubscribed => EitherT.pure[Future, Error](())
          }
    } yield subscriptionResponse
}

object SubscriptionController {

  sealed trait SubscriptionError

  object SubscriptionError {

    final case class BackendError(e: Error) extends SubscriptionError

    final case class RequestValidationError(msg: String) extends SubscriptionError
  }

}
