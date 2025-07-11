/*
 * Copyright 2024 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding

import cats.data.EitherT
import cats.instances.future.*
import cats.syntax.either.*
import com.google.inject.Inject
import play.api.libs.json.{JsString, Json, OFormat, OWrites, Reads}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.{AuthenticateActions, AuthenticatedRequest}
import uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding.SubscriptionController.SubscriptionError.{BackendError, RequestValidationError}
import uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding.SubscriptionController.{GetSubscriptionResponse, SubscriptionError}
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.enrolments.TaxEnrolmentRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails, SubscriptionResponse, SubscriptionUpdateResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.{RegisteredWithoutId, RegistrationDetails}
import uk.gov.hmrc.cgtpropertydisposals.repositories.model.UpdateVerifiersRequest
import uk.gov.hmrc.cgtpropertydisposals.service.enrolments.TaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.{RegisterWithoutIdService, SubscriptionService}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging.*
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class SubscriptionController @Inject() (
  authenticate: AuthenticateActions,
  subscriptionService: SubscriptionService,
  taxEnrolmentService: TaxEnrolmentService,
  registerWithoutIdService: RegisterWithoutIdService,
  cc: ControllerComponents
)(implicit
  ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def subscribe(): Action[AnyContent] =
    authenticate.async { implicit request =>
      val result: EitherT[Future, SubscriptionError, SubscriptionResponse] =
        for
          subscriptionDetails  <- EitherT.fromEither[Future](extractRequest[SubscriptionDetails](request))
          subscriptionResponse <- subscribeAndEnrol(subscriptionDetails)
                                    .leftMap[SubscriptionError](BackendError.apply)
        yield subscriptionResponse

      result.fold(
        {
          case RequestValidationError(msg) =>
            logger.warn(s"Error in request to subscribe: $msg")
            BadRequest
          case BackendError(e)             =>
            logger.warn("Error while trying to subscribe:", e)
            InternalServerError
        },
        {
          case successful: SubscriptionSuccessful => Ok(Json.toJson(successful))
          case AlreadySubscribed                  => Conflict
        }
      )
    }

  def registerWithoutId(): Action[AnyContent] =
    authenticate.async { implicit request =>
      val result = for
        registrationDetails <- EitherT.fromEither[Future](extractRequest[RegistrationDetails](request))
        sapNumber           <- registerWithoutIdService
                                 .registerWithoutId(registrationDetails)
                                 .leftMap[SubscriptionError](BackendError.apply)
      yield sapNumber

      result.fold(
        {
          case RequestValidationError(msg) =>
            logger.warn(s"Error in request to register without id: $msg")
            BadRequest
          case BackendError(e)             =>
            logger.warn("Error while trying to register without id:", e)
            InternalServerError
        },
        sapNumber => Ok(Json.toJson(RegisteredWithoutId(sapNumber)))
      )
    }

  def checkSubscriptionStatus(): Action[AnyContent] =
    authenticate.async { implicit request =>
      taxEnrolmentService.hasCgtSubscription(request.user.ggCredId).value.map {
        case Left(error)                  =>
          logger.warn(s"Error checking existence of enrolment request: $error")
          InternalServerError
        case Right(maybeEnrolmentRequest) =>
          maybeEnrolmentRequest match {
            case Some(enrolmentRequest) => Ok(Json.obj("value" -> JsString(enrolmentRequest.cgtReference)))
            case None                   => NoContent
          }
      }
    }

  def getSubscription(cgtReference: CgtReference): Action[AnyContent] =
    authenticate.async { implicit request =>
      subscriptionService.getSubscription(cgtReference).value.map {
        case Left(error)                   =>
          logger.warn(s"Error getting subscription details: $error")
          InternalServerError
        case Right(maybeSubscribedDetails) =>
          Ok(Json.toJson(GetSubscriptionResponse(maybeSubscribedDetails)))
      }
    }

  def updateSubscription(): Action[AnyContent] =
    authenticate.async { implicit request =>
      val result: EitherT[Future, SubscriptionError, SubscriptionUpdateResponse] =
        for
          subscribedUpdateDetails <- EitherT.fromEither[Future](extractRequest[SubscribedUpdateDetails](request))
          subscriptionResponse    <- subscriptionService
                                       .updateSubscription(subscribedUpdateDetails)
                                       .leftMap[SubscriptionError](BackendError.apply)
          _                       <- taxEnrolmentService
                                       .updateVerifiers(UpdateVerifiersRequest(request.user.ggCredId, subscribedUpdateDetails))
                                       .leftMap[SubscriptionError](BackendError.apply)
        yield subscriptionResponse

      result.fold(
        {
          case RequestValidationError(msg) =>
            logger.warn(s"Error in request to update subscription: $msg")
            BadRequest
          case BackendError(e)             =>
            logger.warn("Error while trying to update subscription:", e)
            InternalServerError
        },
        subscriptionUpdateResponse => Ok(Json.toJson(subscriptionUpdateResponse))
      )
    }

  private def extractRequest[R : Reads](request: Request[AnyContent]): Either[SubscriptionError, R] =
    Either
      .fromOption(request.body.asJson, RequestValidationError("No JSON body found in request"))
      .flatMap(
        _.validate[R].asEither
          .leftMap(e =>
            RequestValidationError(
              s"Could not parse JSON body as register and subscribe request: $e"
            )
          )
      )

  private def subscribeAndEnrol(
    subscriptionDetails: SubscriptionDetails
  )(implicit request: AuthenticatedRequest[?]): EitherT[Future, Error, SubscriptionResponse] =
    for
      subscriptionResponse <- subscriptionService.subscribe(subscriptionDetails)
      _                    <- subscriptionResponse match {
                                case SubscriptionSuccessful(cgtReferenceNumber) =>
                                  taxEnrolmentService.allocateEnrolmentToGroup(
                                    TaxEnrolmentRequest(
                                      request.user.ggCredId,
                                      cgtReferenceNumber,
                                      subscriptionDetails.address,
                                      timestamp = request.timestamp
                                    )
                                  )

                                case AlreadySubscribed => EitherT.pure[Future, Error](())
                              }
    yield subscriptionResponse
}

object SubscriptionController {

  sealed trait SubscriptionError

  object SubscriptionError {

    final case class BackendError(e: Error) extends SubscriptionError

    final case class RequestValidationError(msg: String) extends SubscriptionError

    implicit val backendErrorFormat: OFormat[BackendError]           = Json.format[BackendError]
    implicit val nonUkAddressFormat: OFormat[RequestValidationError] = Json.format[RequestValidationError]
  }

  final case class GetSubscriptionResponse(subscribedDetails: Option[SubscribedDetails])

  object GetSubscriptionResponse {

    implicit val writes: OWrites[GetSubscriptionResponse] = Json.writes[GetSubscriptionResponse]

  }

}
