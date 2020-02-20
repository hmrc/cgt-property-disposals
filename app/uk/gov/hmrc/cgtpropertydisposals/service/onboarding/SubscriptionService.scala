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

import cats.data.EitherT
import cats.instances.future._
import cats.instances.int._
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.http.Status.{ACCEPTED, FORBIDDEN, OK}
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.SubscriptionConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.accounts.{SubscribedDetails, SubscribedUpdateDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, ContactDetails}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.{ContactName, IndividualName, Name, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscriptionResponse.{AlreadySubscribed, SubscriptionSuccessful}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.{SubscriptionDetails, SubscriptionResponse, SubscriptionUpdateResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error, TelephoneNumber}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordServiceImpl.DesBusinessPartnerRecord.DesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordServiceImpl.Validation
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.SubscriptionService.DesSubscriptionDisplayDetails
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[SubscriptionServiceImpl])
trait SubscriptionService {

  def subscribe(subscriptionDetails: SubscriptionDetails, path: String)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscriptionResponse]

  def getSubscription(cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscribedDetails]

  def updateSubscription(subscribedUpdateDetails: SubscribedUpdateDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscriptionUpdateResponse]
}

@Singleton
class SubscriptionServiceImpl @Inject() (
  auditService: AuditService,
  subscriptionConnector: SubscriptionConnector,
  emailConnector: EmailConnector,
  config: Configuration,
  metrics: Metrics
)(
  implicit ec: ExecutionContext
) extends SubscriptionService
    with Logging {

  override def subscribe(
    subscriptionDetails: SubscriptionDetails,
    path: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubscriptionResponse] =
    for {
      subscriptionResponse <- sendSubscriptionRequest(subscriptionDetails, path)
      _ <- subscriptionResponse match {
            case successful: SubscriptionSuccessful =>
              sendSubscriptionConfirmationEmail(subscriptionDetails, successful, path)
            case _ => EitherT.pure[Future, Error](())
          }
    } yield subscriptionResponse

  private def sendSubscriptionRequest(
    subscriptionDetails: SubscriptionDetails,
    path: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, SubscriptionResponse] = {
    def isAlreadySubscribedResponse(response: HttpResponse): Boolean =
      response.status === FORBIDDEN && response
        .parseJSON[DesErrorResponse]()
        .map(_.code)
        .exists(_ === "ACTIVE_SUBSCRIPTION")

    val timer = metrics.subscriptionCreateTimer.time()

    subscriptionConnector.subscribe(subscriptionDetails).subflatMap { response =>
      timer.close()
      auditService.sendSubscriptionResponse(response.status, response.body, path)
      if (response.status === OK) {
        response.parseJSON[SubscriptionSuccessful]().leftMap(Error(_))
      } else if (isAlreadySubscribedResponse(response)) {
        Right(AlreadySubscribed)
      } else {
        metrics.subscriptionCreateErrorCounter.inc()
        Left(Error(s"call to subscribe came back with status ${response.status}"))
      }
    }
  }

  private def sendSubscriptionConfirmationEmail(
    subscriptionDetails: SubscriptionDetails,
    subscriptionSuccessful: SubscriptionSuccessful,
    path: String
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Unit] = {
    val timer = metrics.subscriptionConfirmationEmailTimer.time()
    emailConnector
      .sendSubscriptionConfirmationEmail(subscriptionDetails, CgtReference(subscriptionSuccessful.cgtReferenceNumber))
      .map { httpResponse =>
        timer.close()
        auditService.sendSubscriptionConfirmationEmailSentEvent(
          subscriptionDetails.emailAddress.value,
          subscriptionSuccessful.cgtReferenceNumber,
          path
        )

        if (httpResponse.status =!= ACCEPTED) {
          metrics.subscriptionCreateErrorCounter.inc()
          logger.warn(s"Call to send confirmation email came back with status ${httpResponse.status}")
        }
      }
      .leftFlatMap { e =>
        metrics.subscriptionCreateErrorCounter.inc()
        logger.warn("Could not send confirmation email", e)
        EitherT.pure[Future, Error](())
      }
  }

  override def getSubscription(cgtReference: CgtReference)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscribedDetails] = {
    val timer = metrics.subscriptionGetTimer.time()
    subscriptionConnector.getSubscription(cgtReference).subflatMap { response =>
      timer.close()
      lazy val identifiers =
        List(
          "id"                -> cgtReference.value,
          "DES CorrelationId" -> response.header("CorrelationId").getOrElse("-")
        )

      if (response.status === OK) {
        response
          .parseJSON[DesSubscriptionDisplayDetails]()
          .flatMap(toSubscriptionDisplayRecord(_, cgtReference))
          .leftMap { e =>
            metrics.subscriptionGetErrorCounter.inc()
            Error(e, identifiers: _*)
          }
      } else {
        metrics.subscriptionGetErrorCounter.inc()
        Left(Error(s"call to subscription display api came back with status ${response.status}"))
      }
    }
  }

  override def updateSubscription(subscribedUpdateDetails: SubscribedUpdateDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, SubscriptionUpdateResponse] = {
    val timer = metrics.subscriptionUpdateTimer.time()

    subscriptionConnector.updateSubscription(subscribedUpdateDetails.newDetails).subflatMap { response =>
      timer.close()

      lazy val identifiers =
        List(
          "id"                -> subscribedUpdateDetails.newDetails.cgtReference.value,
          "DES CorrelationId" -> response.header("CorrelationId").getOrElse("-")
        )

      if (response.status === OK) {
        response
          .parseJSON[SubscriptionUpdateResponse]()
          .leftMap { e =>
            metrics.subscriptionUpdateErrorCounter.inc()
            Error(e, identifiers: _*)
          }
      } else {
        metrics.subscriptionUpdateErrorCounter.inc()
        Left(Error(s"call to subscription update api came back with status ${response.status}"))
      }
    }
  }

  private def toSubscriptionDisplayRecord(
    desSubscriptionDisplayDetails: DesSubscriptionDisplayDetails,
    cgtReference: CgtReference
  ): Either[String, SubscribedDetails] = {

    val desNonIsoCountryCodes: List[CountryCode] =
      config.underlying.get[List[CountryCode]]("des.non-iso-country-codes").value

    val addressValidation: Validation[Address] = AddressDetails.fromDesAddressDetails(
      desSubscriptionDisplayDetails.subscriptionDetails.addressDetails
    )(desNonIsoCountryCodes)

    val nameValidation: Validation[Either[TrustName, IndividualName]] = Name.nameValidation(
      desSubscriptionDisplayDetails.subscriptionDetails.typeOfPersonDetails
    )

    val emailValidation: Validation[Email] =
      Email.emailValidation(desSubscriptionDisplayDetails.subscriptionDetails.contactDetails.emailAddress)

    (addressValidation, nameValidation, emailValidation)
      .mapN {
        case (address, name, email) =>
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
