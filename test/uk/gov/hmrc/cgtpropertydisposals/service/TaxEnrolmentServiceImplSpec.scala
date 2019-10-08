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

  def mockAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  def mockInsert(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Boolean]
  ) =
    (mockRepository
      .insert(_: TaxEnrolmentRequest))
      .expects(taxEnrolmentRequest)
      .returning(EitherT[Future, Error, Boolean](Future.successful(response)))

  def mockGet(ggCredId: String)(
    response: Either[Error, Option[TaxEnrolmentRequest]]
  ) =
    (mockRepository
      .get(_: String))
      .expects(ggCredId)
      .returning(EitherT[Future, Error, Option[TaxEnrolmentRequest]](Future.successful(response)))

  def mockDelete(ggCredId: String)(
    response: Either[Error, Int]
  ) =
    (mockRepository
      .delete(_: String))
      .expects(ggCredId)
      .returning(EitherT[Future, Error, Int](Future.successful(response)))

  val (nonUkCountry, nonUkCountryCode) = Country("HK", Some("Hong Kong")) -> "HK"
  implicit val hc: HeaderCarrier       = HeaderCarrier()
  val cgtReference                     = "XACGTP123456789"

  val taxEnrolmentRequestWithNonUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    cgtReference,
    Address.NonUkAddress("line1", None, None, None, Some("OK11KO"), nonUkCountry)
  )

  val taxEnrolmentRequestWithUkAddress = TaxEnrolmentRequest(
    "ggCredId",
    cgtReference,
    Address.UkAddress("line1", None, None, None, "OK11KO")
  )

  "TaxEnrolment Service Implementation" when {

    "it receives a request to check if a user has a CGT enrolment" must {
      "return an error" when {
        "there is mongo exception occurs" in {
          mockGet(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Left(Error("Connection error")))
          await(service.hasCgtEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isLeft shouldBe true
        }
        "there does not exist an enrolment in mongo" in {
          mockGet(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(None))
          await(service.hasCgtEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isLeft shouldBe true
        }
        "there exists a tax enrolment request to retry but the tax enrolment retry fails again" in {
          inSequence {
            mockGet(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(Some(taxEnrolmentRequestWithNonUkAddress)))
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection error")))
          }
          await(service.hasCgtEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isLeft shouldBe true
        }
        "there exists a tax enrolment request to retry and the tax enrolment succeeds but the delete fails" in {
          inSequence {
            mockGet(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(Some(taxEnrolmentRequestWithNonUkAddress)))
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204)))
            mockDelete(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Left(Error("Database error")))
          }
          await(service.hasCgtEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isLeft shouldBe true
        }
      }
      "return true" when {
        "an enrolment record exists, the tax enrolment call is made successfully, and the delete occurred correctly" in {
          inSequence {
            mockGet(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(Some(taxEnrolmentRequestWithNonUkAddress)))
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204)))
            mockDelete(taxEnrolmentRequestWithNonUkAddress.ggCredId)(Right(1))
          }
          await(service.hasCgtEnrolment(taxEnrolmentRequestWithNonUkAddress.ggCredId).value).isRight shouldBe true
        }
      }
    }

    "it receives a request to allocate an enrolment" must {

      "return an error" when {
        "the http call comes back with a status other than 204 and the recording of the enrolment request fails" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsert(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection Error")))
          }
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }
        "the http call comes back with a status other than 204 and the insert into mongo fails" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsert(taxEnrolmentRequestWithNonUkAddress)(Right(false))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }
        "the http call comes back with an exception and the insert into mongo fails" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection error")))
            mockInsert(taxEnrolmentRequestWithNonUkAddress)(Left(Error("Connection Error")))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isLeft shouldBe true
        }
      }

      "return a tax enrolment created success response" when {
        "the http call comes back with a status other than 204" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsert(taxEnrolmentRequestWithNonUkAddress)(Right(true))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status other than no content and the insert into mongo succeeds" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(401)))
            mockInsert(taxEnrolmentRequestWithNonUkAddress)(Right(true))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call fails and the insert into mongo succeeds" in {
          inSequence {
            mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Left(Error("uh oh")))
            mockInsert(taxEnrolmentRequestWithNonUkAddress)(Right(true))
          }

          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }

        "the http call comes back with a status of no content and the address is a UK address with a postcode" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithUkAddress)(Right(HttpResponse(204)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithUkAddress).value).isRight shouldBe true
        }
        "the http call comes back with a status of no content and the address is a Non-UK address with a country code" in {
          mockAllocateEnrolment(taxEnrolmentRequestWithNonUkAddress)(Right(HttpResponse(204)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequestWithNonUkAddress).value).isRight shouldBe true
        }
      }
    }
  }
}
