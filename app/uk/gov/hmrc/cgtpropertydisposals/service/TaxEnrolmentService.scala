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

import cats.data.EitherT
import cats.instances.future._
import com.google.inject.{ImplementedBy, Inject, Singleton}
import play.api.http.Status._
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, EnrolmentRequest, Error, KeyValuePair, SubscriptionDetails}
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse.TaxEnrolmentCreated
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

@ImplementedBy(classOf[TaxEnrolmentServiceImpl])
trait TaxEnrolmentService {
  def allocateEnrolmentToGroup(cgtReference: String, subscriptionDetails: SubscriptionDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, TaxEnrolmentResponse]
}

object TaxEnrolmentService {

  sealed trait TaxEnrolmentResponse

  object TaxEnrolmentResponse {
    case object TaxEnrolmentCreated extends TaxEnrolmentResponse
  }

}

@Singleton
class TaxEnrolmentServiceImpl @Inject()(connector: TaxEnrolmentConnector) extends TaxEnrolmentService {

  override def allocateEnrolmentToGroup(cgtReference: String, subscriptionDetails: SubscriptionDetails)(
    implicit hc: HeaderCarrier
  ): EitherT[Future, Error, TaxEnrolmentResponse] = {

    val enrolmentRequest = subscriptionDetails.address match {
      case Address.UkAddress(line1, line2, line3, line4, postcode) =>
        EnrolmentRequest(List(KeyValuePair("Postcode", postcode)), List(KeyValuePair("CGTPDRef", cgtReference)))
      case Address.NonUkAddress(line1, line2, line3, line4, maybePostcode, countryCode) =>
        maybePostcode match {
          case Some(postcode) =>
            EnrolmentRequest(
              List(KeyValuePair("Postcode", postcode)),
              List(KeyValuePair("CGTPDRef", cgtReference))
            )
          case None =>
            EnrolmentRequest(
              List(KeyValuePair("CountryCode", countryCode)),
              List(KeyValuePair("CGTPDRef", cgtReference))
            )
        }
    }

    connector
      .allocateEnrolmentToGroup(cgtReference, enrolmentRequest)
      .subflatMap { response =>
        response.status match {
          case NO_CONTENT =>
            Right(TaxEnrolmentCreated)
          case UNAUTHORIZED =>
            Left(Error("Unauthorized"))
          case BAD_REQUEST =>
            Left(Error("Bad request"))
          case other => Left(Error(s"Received unexpected http status in response from tax-enrolment service"))
        }
      }
  }
}
