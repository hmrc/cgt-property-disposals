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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{EitherT, NonEmptyList}
import cats.instances.future._
import cats.instances.int._
import cats.instances.string._
import cats.syntax.apply._
import cats.syntax.either._
import cats.syntax.eq._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import configs.syntax._
import play.api.Configuration
import play.api.http.Status.{NOT_FOUND, OK}
import play.api.libs.json.{Json, Reads}
import uk.gov.hmrc.cgtpropertydisposals.connectors.onboarding.{BusinessPartnerRecordConnector, SubscriptionConnector}
import uk.gov.hmrc.cgtpropertydisposals.metrics.Metrics
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, CountryCodeMapping}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.des.{AddressDetails, SubscriptionStatus}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, SapNumber}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, Validation}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordServiceImpl.DesBusinessPartnerRecord.DesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordServiceImpl.{DesBusinessPartnerRecord, DesSubscriptionStatusResponse}
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BusinessPartnerRecordServiceImpl])
trait BusinessPartnerRecordService {

  def getBusinessPartnerRecord(bprRequest: BusinessPartnerRecordRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, BusinessPartnerRecordResponse]

}

@Singleton
class BusinessPartnerRecordServiceImpl @Inject() (
  bprConnector: BusinessPartnerRecordConnector,
  subscriptionConnector: SubscriptionConnector,
  config: Configuration,
  metrics: Metrics
)(
  implicit ec: ExecutionContext
) extends BusinessPartnerRecordService {

  val desNonIsoCountryCodes: List[CountryCode] =
    config.underlying.get[List[CountryCode]]("des.non-iso-country-codes").value

  val countryCodeMappings: Map[CountryCode, CountryCode] =
    config.underlying.get[List[CountryCodeMapping]]("des.country-code-mappings").value.map(m => m.from -> m.to).toMap

  val correlationIdHeaderKey = "CorrelationId"

  def getBusinessPartnerRecord(bprRequest: BusinessPartnerRecordRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, BusinessPartnerRecordResponse] =
    for {
      maybeBpr <- getBpr(bprRequest)
      maybeCgtRef <- maybeBpr.fold[EitherT[Future, Error, Option[CgtReference]]](EitherT.pure(None))(bpr =>
                      getSubscriptionStatus(bpr.sapNumber)
                    )
    } yield BusinessPartnerRecordResponse(maybeBpr, maybeCgtRef)

  private def getBpr(
    bprRequest: BusinessPartnerRecordRequest
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[BusinessPartnerRecord]] = {
    val timer = metrics.registerWithIdTimer.time()

    bprConnector.getBusinessPartnerRecord(bprRequest).subflatMap { response =>
      timer.close()
      lazy val identifiers =
        List(
          "id"                -> bprRequest.foldOnId(_.value, _.value, _.value),
          "DES CorrelationId" -> response.header(correlationIdHeaderKey).getOrElse("-")
        )

      if (response.status === OK) {
        response
          .parseJSON[DesBusinessPartnerRecord]()
          .flatMap(toBusinessPartnerRecord(_))
          .map(Some(_))
          .leftMap { e =>
            metrics.registerWithIdErrorCounter.inc()
            Error(e, identifiers: _*)
          }
      } else if (isNotFoundResponse(response)) {
        Right(None)
      } else {
        metrics.registerWithIdErrorCounter.inc()
        Left(Error(s"Call to get BPR came back with status ${response.status}", identifiers: _*))
      }
    }
  }

  private def getSubscriptionStatus(
    sapNumber: SapNumber
  )(implicit hc: HeaderCarrier): EitherT[Future, Error, Option[CgtReference]] = {
    val timer = metrics.subscriptionStatusTimer.time()

    subscriptionConnector.getSubscriptionStatus(sapNumber).subflatMap { response =>
      timer.close()
      if (response.status === OK) {
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
      } else {
        metrics.subscriptionStatusErrorCounter.inc()
        Left(Error(s"Call to get subscription status came back with status ${response.status}"))
      }
    }
  }

  private def isNotFoundResponse(response: HttpResponse): Boolean =
    // check that a 404 response has actually come from DES by inspecting the body
    response.status === NOT_FOUND && response.parseJSON[DesErrorResponse]().map(_.code).exists(_ === "NOT_FOUND")

  private def toBusinessPartnerRecord(d: DesBusinessPartnerRecord): Either[String, BusinessPartnerRecord] = {
    val a = d.address

    val addressValidation: Validation[Address] =
      AddressDetails.fromDesAddressDetails(a)(desNonIsoCountryCodes, countryCodeMappings)

    val nameValidation: Validation[Either[TrustName, IndividualName]] =
      d.individual -> d.organisation match {
        case (Some(individual), None)   => Valid(Right(IndividualName(individual.firstName, individual.lastName)))
        case (None, Some(organisation)) => Valid(Left(TrustName(organisation.organisationName)))
        case (Some(_), Some(_)) =>
          Invalid(NonEmptyList.one("BPR contained both an organisation name and individual name"))
        case (None, None) =>
          Invalid(NonEmptyList.one("BPR contained contained neither an organisation name or an individual name"))
      }

    (addressValidation, nameValidation)
      .mapN {
        case (address, name) =>
          BusinessPartnerRecord(
            d.contactDetails.emailAddress,
            address,
            SapNumber(d.sapNumber),
            name
          )
      }
      .toEither
      .leftMap(errors => s"Could not read DES response: ${errors.toList.mkString("; ")}")
  }

}

object BusinessPartnerRecordServiceImpl {

  import DesBusinessPartnerRecord._

  final case class DesBusinessPartnerRecord(
    address: AddressDetails,
    contactDetails: DesContactDetails,
    sapNumber: String,
    organisation: Option[DesOrganisation],
    individual: Option[DesIndividual]
  )

  final case class DesSubscriptionStatusResponse(
    subscriptionStatus: SubscriptionStatus,
    idType: Option[String],
    idValue: Option[String]
  )

  object DesBusinessPartnerRecord {
    final case class DesOrganisation(organisationName: String)

    final case class DesIndividual(
      firstName: String,
      lastName: String
    )

    final case class DesContactDetails(emailAddress: Option[String])

    final case class DesErrorResponse(code: String, reason: String)

    implicit val organisationReads: Reads[DesOrganisation]     = Json.reads
    implicit val individualReads: Reads[DesIndividual]         = Json.reads
    implicit val contactDetailsReads: Reads[DesContactDetails] = Json.reads
    implicit val bprReads: Reads[DesBusinessPartnerRecord]     = Json.reads
    implicit val errorResponseReads: Reads[DesErrorResponse]   = Json.reads
  }

  object DesSubscriptionStatusResponse {

    implicit val reads: Reads[DesSubscriptionStatusResponse] = Json.reads

  }

}
