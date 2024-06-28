/*
 * Copyright 2023 HM Revenue & Customs
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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status.{FORBIDDEN, NOT_FOUND, OK}
import play.api.libs.json.Json
import play.api.mvc.Request
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.SubscriptionConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.SubscribedUpdateDetails
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.des.DesErrorResponse.SingleDesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.models.des._
import uk.gov.hmrc.cgtpropertydisposals.models.des.onboarding.DesSubscriptionRequest
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, Name, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.SubscriptionResponseEvent
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscribedDetails, SubscriptionDetails, SubscriptionResponse, SubscriptionUpdateResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber, Validation}
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.email.EmailService
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordServiceImpl.DesSubscriptionStatusResponse
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.SubscriptionService.DesSubscriptionDisplayDetails
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionServiceImpl])
trait SubscriptionService {

  def subscribe(subscriptionDetails: SubscriptionDetails)(implicit
    hc: HeaderCarrier,
    request: Request[_]
  ): EitherT[Future, Error, SubscriptionResponse]

  def getSubscription(cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[SubscribedDetails]]

  def updateSubscription(subscribedUpdateDetails: SubscribedUpdateDetails)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, SubscriptionUpdateResponse]

  def getSubscriptionStatus(
    sapNumber: SapNumber
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[CgtReference]]
}

@Singleton
class SubscriptionServiceImpl @Inject() (
  auditService: AuditService,
  subscriptionConnector: SubscriptionConnector,
  emailService: EmailService,
  metrics: Metrics
)(implicit
  ec: ExecutionContext
) extends SubscriptionService
    with Logging {

  override def subscribe(
    subscriptionDetails: SubscriptionDetails
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, SubscriptionResponse] =
    for {
      subscriptionResponse <- sendSubscriptionRequest(subscriptionDetails)
      _                    <- subscriptionResponse match {
                                case successful: SubscriptionSuccessful =>
                                  emailService
                                    .sendSubscriptionConfirmationEmail(
                                      CgtReference(successful.cgtReferenceNumber),
                                      subscriptionDetails.emailAddress,
                                      subscriptionDetails.contactName
                                    )
                                    .leftFlatMap(e =>
                                      EitherT.pure[Future, Error](logger.warn("Could not send subscription confirmation email", e))
                                    )
                                case _                                  => EitherT.pure[Future, Error](())
                              }
    } yield subscriptionResponse

  private def sendSubscriptionRequest(
    subscriptionDetails: SubscriptionDetails
  )(implicit hc: HeaderCarrier, request: Request[_]): EitherT[Future, Error, SubscriptionResponse] = {
    def isAlreadySubscribedResponse(response: HttpResponse): Boolean =
      response.status === FORBIDDEN && response
        .parseJSON[DesErrorResponse]()
        .exists(_.hasCode("ACTIVE_SUBSCRIPTION"))

    val desSubscriptionRequest = DesSubscriptionRequest(subscriptionDetails)
    val timer                  = metrics.subscriptionCreateTimer.time()

    subscriptionConnector.subscribe(desSubscriptionRequest).subflatMap { response =>
      timer.close()
      auditSubscriptionResponse(response.status, response.body, desSubscriptionRequest)
      if (response.status === OK)
        response.parseJSON[SubscriptionSuccessful]().leftMap(Error(_))
      else if (isAlreadySubscribedResponse(response))
        Right(AlreadySubscribed)
      else {
        metrics.subscriptionCreateErrorCounter.inc()
        Left(Error(s"call to subscribe came back with status ${response.status}"))
      }
    }
  }

  override def getSubscription(cgtReference: CgtReference)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, Option[SubscribedDetails]] = {
    def isNoSubscriptionExistsResponse(response: HttpResponse): Boolean = {
      lazy val hasNoReturnBody = response
        .parseJSON[DesErrorResponse]()
        .bimap(
          _ => false,
          _.hasError(SingleDesErrorResponse("NOT_FOUND", "Data not found for the provided Registration Number."))
        )
        .merge

      response.status === NOT_FOUND && hasNoReturnBody
    }

    val timer = metrics.subscriptionGetTimer.time()
    subscriptionConnector.getSubscription(cgtReference).subflatMap { response =>
      timer.close()
      lazy val identifiers =
        List(
          "id"                -> cgtReference.value,
          "DES CorrelationId" -> response.header("CorrelationId").getOrElse("-")
        )

      if (response.status === OK)
        response
          .parseJSON[DesSubscriptionDisplayDetails]()
          .flatMap(toSubscriptionDisplayRecord(_, cgtReference))
          .bimap(
            { e =>
              metrics.subscriptionGetErrorCounter.inc()
              Error(e, identifiers: _*)
            },
            Some(_)
          )
      else if (isNoSubscriptionExistsResponse(response))
        Right(None)
      else {
        metrics.subscriptionGetErrorCounter.inc()
        Left(Error(s"call to subscription display api came back with status ${response.status}"))
      }
    }
  }

  override def updateSubscription(subscribedUpdateDetails: SubscribedUpdateDetails)(implicit
    hc: HeaderCarrier
  ): EitherT[Future, Error, SubscriptionUpdateResponse] = {
    val desSubscriptionRequest = DesSubscriptionUpdateRequest(subscribedUpdateDetails.newDetails)
    val timer                  = metrics.subscriptionUpdateTimer.time()

    subscriptionConnector
      .updateSubscription(desSubscriptionRequest, subscribedUpdateDetails.newDetails.cgtReference)
      .subflatMap { response =>
        timer.close()

        lazy val identifiers =
          List(
            "id"                -> subscribedUpdateDetails.newDetails.cgtReference.value,
            "DES CorrelationId" -> response.header("CorrelationId").getOrElse("-")
          )

        if (response.status === OK)
          response
            .parseJSON[SubscriptionUpdateResponse]()
            .leftMap { e =>
              metrics.subscriptionUpdateErrorCounter.inc()
              Error(e, identifiers: _*)
            }
        else {
          metrics.subscriptionUpdateErrorCounter.inc()
          Left(Error(s"call to subscription update api came back with status ${response.status}"))
        }
      }
  }

  def getSubscriptionStatus(
    sapNumber: SapNumber
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[CgtReference]] = {
    val timer = metrics.subscriptionStatusTimer.time()

    subscriptionConnector.getSubscriptionStatus(sapNumber).subflatMap { response =>
      timer.close()
      if (response.status === OK)
        response
          .parseJSON[DesSubscriptionStatusResponse]()
          .leftMap { e =>
            metrics.subscriptionStatusErrorCounter.inc()
            Error(e)
          }
          .flatMap {
            case DesSubscriptionStatusResponse(SubscriptionStatus.Subscribed, Some("ZCGT"), Some(cgtRef)) =>
              Right(Some(CgtReference(cgtRef)))

            case DesSubscriptionStatusResponse(SubscriptionStatus.Subscribed, otherIdType, id) =>
              Left(
                Error(
                  s"Could not find cgt reference id in subscription status response. Got id type " +
                    s"${otherIdType.getOrElse("-")} with id ${id.getOrElse("-")}"
                )
              )

            case DesSubscriptionStatusResponse(_, _, _) =>
              Right(None)
          }
      else {
        metrics.subscriptionStatusErrorCounter.inc()
        Left(Error(s"Call to get subscription status came back with status ${response.status}"))
      }
    }
  }

  private def toSubscriptionDisplayRecord(
    desSubscriptionDisplayDetails: DesSubscriptionDisplayDetails,
    cgtReference: CgtReference
  ): Either[String, SubscribedDetails] = {
    val addressValidation: Validation[Address] = AddressDetails.fromDesAddressDetails(
      desSubscriptionDisplayDetails.subscriptionDetails.addressDetails,
      allowNonIsoCountryCodes = true
    )

    val nameValidation: Validation[Either[TrustName, IndividualName]] = Name.nameValidation(
      desSubscriptionDisplayDetails.subscriptionDetails.typeOfPersonDetails
    )

    val emailValidation: Validation[Email] =
      Email.emailValidation(desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.emailAddress)

    (addressValidation, nameValidation, emailValidation)
      .mapN { case (address, name, email) =>
        SubscribedDetails(
          name,
          email,
          address,
          ContactName(desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.contactName),
          cgtReference,
          desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.phoneNumber
            .map(t => TelephoneNumber(t)),
          desSubscriptionDisplayDetails.subscriptionDetails.isRegisteredWithId
        )
      }
      .toEither
      .leftMap(errors => s"Could not read DES response: ${errors.toList.mkString("; ")}")
  }

  private def auditSubscriptionResponse(
    responseHttpStatus: Int,
    responseBody: String,
    desSubscriptionRequest: DesSubscriptionRequest
  )(implicit hc: HeaderCarrier, request: Request[_]): Unit = {
    val responseJson =
      if ((responseHttpStatus === OK && responseBody.nonEmpty) || responseHttpStatus === FORBIDDEN) {
        Json.parse(responseBody)
      } else {
        Json.obj("body : could not parse body as JSON: " -> responseBody)
      }

    auditService.sendEvent(
      "subscriptionResponse",
      SubscriptionResponseEvent(responseHttpStatus, responseJson, desSubscriptionRequest),
      "subscription-response"
    )
  }

}

object SubscriptionService {

  import play.api.libs.json.{Json, OFormat}

  final case class TypeOfPersonDetails(
    typeOfPerson: String,
    firstName: Option[String],
    lastName: Option[String],
    organisationName: Option[String]
  )

  object TypeOfPersonDetails {
    implicit val format: OFormat[TypeOfPersonDetails] = Json.format[TypeOfPersonDetails]
  }

  final case class DesSubscriptionDisplayDetails(
    regime: String,
    subscriptionDetails: DesSubscribedDetails
  )

  object DesSubscriptionDisplayDetails {
    implicit val format: OFormat[DesSubscriptionDisplayDetails] = Json.format[DesSubscriptionDisplayDetails]
  }

  final case class DesSubscribedDetails(
    typeOfPersonDetails: TypeOfPersonDetails,
    isRegisteredWithId: Boolean,
    addressDetails: AddressDetails,
    contactDetails: ContactDetails
  )

  object DesSubscribedDetails {
    implicit val format: OFormat[DesSubscribedDetails] = Json.format[DesSubscribedDetails]
  }

}
