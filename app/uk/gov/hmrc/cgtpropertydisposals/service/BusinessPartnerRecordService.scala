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

import cats.syntax.either._
import cats.syntax.eq._
import cats.instances.int._
import cats.instances.string._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.libs.json.{Format, Json, Reads}
import uk.gov.hmrc.cgtpropertydisposals.connectors.BusinessPartnerRecordConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Address.{NonUkAddress, UkAddress}
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, BusinessPartnerRecord, DateOfBirth, Error, NINO}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordServiceImpl.DesBusinessPartnerRecord
import uk.gov.hmrc.cgtpropertydisposals.util.HttpResponseOps._
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

@ImplementedBy(classOf[BusinessPartnerRecordServiceImpl])
trait BusinessPartnerRecordService {

  def getBusinessPartnerRecord(nino: NINO)(implicit hc: HeaderCarrier): Future[Either[Error, BusinessPartnerRecord]]

}

@Singleton
class BusinessPartnerRecordServiceImpl @Inject() (connector: BusinessPartnerRecordConnector)(implicit ec: ExecutionContext) extends BusinessPartnerRecordService {

  def getBusinessPartnerRecord(nino: NINO)(implicit hc: HeaderCarrier): Future[Either[Error, BusinessPartnerRecord]] =
    connector.getBusinessPartnerRecord(nino).map(response =>
      if (response.status === 200) {
        response.parseJSON[DesBusinessPartnerRecord]().flatMap(toBusinessPartnerRecord).leftMap(Error.apply)
      } else {
        Left(Error(s"Call to get BPR came back with status ${response.status}"))
      }).recover {
      case e => Left(Error(e))
    }

  def toBusinessPartnerRecord(d: DesBusinessPartnerRecord): Either[String, BusinessPartnerRecord] = {
    val a = d.address

    val maybeAddress: Either[String, Address] =
      if (a.countryCode === "GB") {
        a.postalCode.fold[Either[String, UkAddress]](
          Left("Could not find postcode for UK address")
        )(p =>
            Right(UkAddress(a.addressLine1, a.addressLine2, a.addressLine3, a.addressLine4, p)))
      } else {
        Right(NonUkAddress(a.addressLine1, a.addressLine2, a.addressLine3, a.addressLine4, a.postalCode, a.countryCode))
      }

    maybeAddress.map(a => BusinessPartnerRecord(d.individual.firstName, d.individual.lastName, d.individual.dateOfBirth, d.contactDetails.emailAddress, a))
  }

}

object BusinessPartnerRecordServiceImpl {

  import DesBusinessPartnerRecord._

  final case class DesBusinessPartnerRecord(
      individual: DesIndividual,
      address: DesAddress,
      contactDetails: DesContactDetails
  )

  object DesBusinessPartnerRecord {
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
        lastName: String,
        dateOfBirth: DateOfBirth
    )

    final case class DesContactDetails(emailAddress: Option[String])

    implicit val addressReads: Reads[DesAddress] = Json.reads
    implicit val individualReads: Reads[DesIndividual] = Json.reads
    implicit val contactDetailsReads: Reads[DesContactDetails] = Json.reads
    implicit val bprReads: Reads[DesBusinessPartnerRecord] = Json.reads
  }

}

