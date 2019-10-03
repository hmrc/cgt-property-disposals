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

import cats.data.Validated.{Invalid, Valid}
import cats.data.{EitherT, NonEmptyList, ValidatedNel}
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
import uk.gov.hmrc.cgtpropertydisposals.connectors.BusinessPartnerRecordConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.address.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.address.Country.CountryCode
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.bpr.{BusinessPartnerRecord, BusinessPartnerRecordRequest, BusinessPartnerRecordResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.name.{IndividualName, TrustName}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordServiceImpl.DesBusinessPartnerRecord.DesErrorResponse
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordServiceImpl.{DesBusinessPartnerRecord, Validation}
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
class BusinessPartnerRecordServiceImpl @Inject()(connector: BusinessPartnerRecordConnector, config: Configuration)(
  implicit ec: ExecutionContext
) extends BusinessPartnerRecordService {

  val desNonIsoCountryCodes: List[CountryCode] =
    config.underlying.get[List[CountryCode]]("des.non-iso-country-codes").value

  def getBusinessPartnerRecord(bprRequest: BusinessPartnerRecordRequest)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, BusinessPartnerRecordResponse] =
    connector.getBusinessPartnerRecord(bprRequest).subflatMap { response =>
      lazy val identifiers =
        List(
          "id"                -> bprRequest.id.fold(_.value, _.value),
          "DES CorrelationId" -> response.header(correlationIdHeaderKey).getOrElse("-")
        )

      if (response.status === OK) {
        response
          .parseJSON[DesBusinessPartnerRecord]()
          .flatMap(toBusinessPartnerRecord(_).map(bpr => BusinessPartnerRecordResponse(Some(bpr))))
          .leftMap(Error(_, identifiers: _*))
      } else if (isNotFoundResponse(response)) {
        Right(BusinessPartnerRecordResponse(None))
      } else {
        Left(Error(s"Call to get BPR came back with status ${response.status}", identifiers: _*))
      }
    }

  val correlationIdHeaderKey = "CorrelationId"

  def isNotFoundResponse(response: HttpResponse): Boolean =
    // check that a 404 response has actually come from DES by inspecting the body
    response.status === NOT_FOUND && response.parseJSON[DesErrorResponse]().map(_.code).exists(_ === "NOT_FOUND")

  def toBusinessPartnerRecord(d: DesBusinessPartnerRecord): Either[String, BusinessPartnerRecord] = {
    val a = d.address

    val addressValidation: Validation[Address] = {
      if (a.countryCode === "GB") {
        a.postalCode.fold[ValidatedNel[String, Address]](
          Invalid(NonEmptyList.one("Could not find postcode for UK address"))
        )(p => Valid(UkAddress(a.addressLine1, a.addressLine2, a.addressLine3, a.addressLine4, p)))
      } else {
        val country = Country.countryCodeToCountryName.get(a.countryCode) match {
          case Some(countryName)                                     => Some(Country(a.countryCode, Some(countryName)))
          case None if desNonIsoCountryCodes.contains(a.countryCode) => Some(Country(a.countryCode, None))
          case None                                                  => None
        }

        country.fold[ValidatedNel[String, Address]](
          Invalid(NonEmptyList.one(s"Received unknown country code: ${a.countryCode}"))
        )(c => Valid(NonUkAddress(a.addressLine1, a.addressLine2, a.addressLine3, a.addressLine4, a.postalCode, c)))
      }
    }

    val nameValidation: Validation[Either[TrustName, IndividualName]] =
      d.individual -> d.organisation match {
        case (Some(individual), None)   => Valid(Right(IndividualName(individual.firstName, individual.lastName)))
        case (None, Some(organisation)) => Valid(Left(TrustName(organisation.name)))
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
            d.sapNumber,
            name
          )
      }
      .toEither
      .leftMap(errors => s"Could not read DES response: ${errors.toList.mkString("; ")}")
  }

}

object BusinessPartnerRecordServiceImpl {

  import DesBusinessPartnerRecord._

  type Validation[A] = ValidatedNel[String, A]

  final case class DesBusinessPartnerRecord(
    address: DesAddress,
    contactDetails: DesContactDetails,
    sapNumber: String,
    organisation: Option[DesOrganisation],
    individual: Option[DesIndividual]
  )

  object DesBusinessPartnerRecord {
    final case class DesOrganisation(name: String)

    final case class DesAddress(
      addressLine1: String,
      addressLine2: Option[String],
      addressLine3: Option[String],
      addressLine4: Option[String],
      postalCode: Option[String],
      countryCode: String
    )

    final case class DesIndividual(
      firstName: String,
      lastName: String
    )

    final case class DesContactDetails(emailAddress: Option[String])

    final case class DesErrorResponse(code: String, reason: String)

    implicit val organisationReads: Reads[DesOrganisation]     = Json.reads
    implicit val addressReads: Reads[DesAddress]               = Json.reads
    implicit val individualReads: Reads[DesIndividual]         = Json.reads
    implicit val contactDetailsReads: Reads[DesContactDetails] = Json.reads
    implicit val bprReads: Reads[DesBusinessPartnerRecord]     = Json.reads
    implicit val errorResponseReads: Reads[DesErrorResponse]   = Json.reads
  }

}
