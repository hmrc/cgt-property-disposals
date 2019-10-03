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
import uk.gov.hmrc.cgtpropertydisposals.models.address.{Address, Country}
import uk.gov.hmrc.cgtpropertydisposals.models.{Error, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class TaxEnrolmentServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: TaxEnrolmentConnector   = mock[TaxEnrolmentConnector]
  val mockRepository: TaxEnrolmentRepository = mock[TaxEnrolmentRepository]

  val service = new TaxEnrolmentServiceImpl(mockConnector, mockRepository)

  def mockAllocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  def mockInsertTaxEnrolmentRequestToMongoDB(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Boolean]
  ) =
    (mockRepository
      .insert(_: TaxEnrolmentRequest))
      .expects(taxEnrolmentRequest)
      .returning(EitherT[Future, Error, Boolean](Future.successful(response)))

  val (nonUkCountry, nonUkCountryCode) = Country("HK", Some("Hong Kong")) -> "HK"
  implicit val hc: HeaderCarrier       = HeaderCarrier()
  val cgtReference                     = "XACGTP123456789"

  val taxEnrolmentRequestWithNonAddress = TaxEnrolmentRequest(
    "userId-1",
    cgtReference,
    Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), nonUkCountry)
  )

  val taxEnrolmentRequestWithUkAddress = TaxEnrolmentRequest(
    "userId-1",
    cgtReference,
    Address.UkAddress("line1", None, None, None, "OK11KO")
  )

  "TaxEnrolment Service Implementation" when {
    "it receives a request to allocate an enrolment" must {
      "return an error" when {
        "the http call comes back with a status other than 204 and the recording of the enrolment request fails" in {
          mockInsertTaxEnrolmentRequestToMongoDB(taxEnrolmentRequestWithNonAddress)(Left(Error("Connection Error")))
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress)(Right(HttpResponse(401)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress).value).isLeft shouldBe true
        }
        "the http call comes back with a status other than 204 and the insert into mongo fails" in {
          mockInsertTaxEnrolmentRequestToMongoDB(taxEnrolmentRequestWithNonAddress)(Right(false))
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress)(Right(HttpResponse(401)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress).value).isLeft shouldBe true
        }
        "the http call comes back with an exception and the insert into mongo fails" in {
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress)(Left(Error("Connection error")))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress).value).isLeft shouldBe true
        }
      }
      "return a tax enrolment created success response" when {
        "the http call comes back with a status other than 204" in {
          mockInsertTaxEnrolmentRequestToMongoDB(taxEnrolmentRequestWithNonAddress)(Right(true))
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress)(Right(HttpResponse(401)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status other than no content and the insert into mongo succeeds" in {
          mockInsertTaxEnrolmentRequestToMongoDB(taxEnrolmentRequestWithNonAddress)(Right(true))
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress)(Right(HttpResponse(401)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status of no content and the address is a UK address with a postcode" in {
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithUkAddress)(Right(HttpResponse(204)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithUkAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status of no content and the address is a Non-UK address with a country code" in {
          mockAllocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress)(Right(HttpResponse(204)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonAddress).value).isRight shouldBe true
        }
      }
    }
  }
}
