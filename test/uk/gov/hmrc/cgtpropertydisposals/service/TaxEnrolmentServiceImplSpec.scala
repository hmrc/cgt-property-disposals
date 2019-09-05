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
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.TaxEnrolmentConnector
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, EnrolmentRequest, Error, KeyValuePair, Name, SubscriptionDetails}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class TaxEnrolmentServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: TaxEnrolmentConnector = mock[TaxEnrolmentConnector]

  val service = new TaxEnrolmentServiceImpl(mockConnector)

  def mockAllocateEnrolmentToGroup(cgtReference: String, enrolmentRequest: EnrolmentRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .allocateEnrolmentToGroup(_: String, _: EnrolmentRequest)(_: HeaderCarrier))
      .expects(cgtReference, enrolmentRequest, *)
      .returning(EitherT(Future.successful(response)))

  "TaxEnrolment Service Implementation" when {

    "it receives a request to allocate an enrolment it" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      "return an error" when {
        val cgtReference = "XACGTP123456789"

        "the http call comes back with a status of unauthorized" in {
          val enrolmentRequest =
            EnrolmentRequest(
              List(KeyValuePair("Postcode", "OK11KO")),
              List(KeyValuePair("CGTPDRef", cgtReference))
            )
          val subscriptionDetails = SubscriptionDetails(
           Right(Name("firstname", "lastname")),
            "firstname.lastname@gmail.com",
            Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), "GB"),
            "sapNumber"
          )

          mockAllocateEnrolmentToGroup(cgtReference, enrolmentRequest)(Right(HttpResponse(401)))

          await(service.allocateEnrolmentToGroup(cgtReference, subscriptionDetails).value).isLeft shouldBe true
        }

        "the http call comes back with a status of bad request" in {
          val enrolmentRequest =
            EnrolmentRequest(
              List(KeyValuePair("Postcode", "OK11KO")),
              List(KeyValuePair("CGTPDRef", cgtReference))
            )
          val subscriptionDetails = SubscriptionDetails(
            Right(Name("firstname", "lastname")),
            "firstname.lastname@gmail.com",
            Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), "GB"),
            "sapNumber"
          )
          mockAllocateEnrolmentToGroup(cgtReference, enrolmentRequest)(Right(HttpResponse(400)))

          await(service.allocateEnrolmentToGroup(cgtReference, subscriptionDetails).value).isLeft shouldBe true
        }

        "the http call comes back with any other non-successful status" in {
          val enrolmentRequest =
            EnrolmentRequest(
              List(KeyValuePair("Postcode", "OK11KO")),
              List(KeyValuePair("CGTPDRef", cgtReference))
            )
          val subscriptionDetails = SubscriptionDetails(
            Right(Name("firstname", "lastname")),
            "firstname.lastname@gmail.com",
            Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), "GB"),
            "sapNumber"
          )
          mockAllocateEnrolmentToGroup(cgtReference, enrolmentRequest)(Right(HttpResponse(500)))

          await(service.allocateEnrolmentToGroup(cgtReference, subscriptionDetails).value).isLeft shouldBe true
        }

      }

      "return a tax enrolment created success response" when {
        val cgtReference = "XACGTP123456789"

        "the http call comes back with a status of no content and the address is a UK address with a postcode" in {
          val enrolmentRequest =
            EnrolmentRequest(List(KeyValuePair("Postcode", "OK11KO")), List(KeyValuePair("CGTPDRef", cgtReference)))
          val subscriptionDetails = SubscriptionDetails(
            Right(Name("firstname", "lastname")),
            "firstname.lastname@gmail.com",
            Address.UkAddress("line1", None, None, None, "OK11KO"),
            "sapNumber"
          )

          mockAllocateEnrolmentToGroup(cgtReference, enrolmentRequest)(Right(HttpResponse(204)))

          await(service.allocateEnrolmentToGroup(cgtReference, subscriptionDetails).value).isRight shouldBe true
        }

        "the http call comes back with a status of no content and the address is a non-uk address with a postcode" in {
          val enrolmentRequest =
            EnrolmentRequest(
              List(KeyValuePair("Postcode", "OK11KO")),
              List(KeyValuePair("CGTPDRef", cgtReference))
            )
          val subscriptionDetails = SubscriptionDetails(
            Right(Name("firstname", "lastname")),
            "firstname.lastname@gmail.com",
            Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), "GB"),
            "sapNumber"
          )

          mockAllocateEnrolmentToGroup(cgtReference, enrolmentRequest)(Right(HttpResponse(204)))

          await(service.allocateEnrolmentToGroup(cgtReference, subscriptionDetails).value).isRight shouldBe true
        }

        "the http call comes back with a status of no content and the address is a non-uk address with no country code and a post code" in {
          val enrolmentRequest =
            EnrolmentRequest(List(KeyValuePair("CountryCode", "GB")), List(KeyValuePair("CGTPDRef", cgtReference)))
          val subscriptionDetails = SubscriptionDetails(
            Right(Name("firstname", "lastname")),
            "firstname.lastname@gmail.com",
            Address.NonUkAddress("line1", None, None, None, None, "GB"),
            "sapNumber"
          )

          mockAllocateEnrolmentToGroup(cgtReference, enrolmentRequest)(Right(HttpResponse(204)))

          await(service.allocateEnrolmentToGroup(cgtReference, subscriptionDetails).value).isRight shouldBe true
        }
      }
    }
  }
}
