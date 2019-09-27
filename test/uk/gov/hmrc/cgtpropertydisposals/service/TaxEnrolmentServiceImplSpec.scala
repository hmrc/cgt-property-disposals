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
import uk.gov.hmrc.cgtpropertydisposals.models.{Address, Error, TaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.repositories.TaxEnrolmentRetryRepository
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class TaxEnrolmentServiceImplSpec extends WordSpec with Matchers with MockFactory {

  val mockConnector: TaxEnrolmentConnector        = mock[TaxEnrolmentConnector]
  val mockRepository: TaxEnrolmentRetryRepository = mock[TaxEnrolmentRetryRepository]

  val service = new TaxEnrolmentServiceImpl(mockConnector, mockRepository)

  def mockConnectorToAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, HttpResponse]
  ) =
    (mockConnector
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT[Future, Error, HttpResponse](Future.successful(response)))

  def mockRepositoryToAllocateEnrolment(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[Error, Boolean]
  ) =
    (mockRepository
      .insert(_: TaxEnrolmentRequest))
      .expects(taxEnrolmentRequest)
      .returning(EitherT[Future, Error, Boolean](Future.successful(response)))

  "TaxEnrolment Service Implementation" when {

    "it receives a request to allocate an enrolment it" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      "return an error" when {
        "the http call comes back with a status of unauthorized" in {
          val cgtReference = "XACGTP123456789"
          val taxEnrolmentRequest = TaxEnrolmentRequest(
            "userId-1",
            cgtReference,
            Address.UkAddress("line1", None, None, None, "KO11OK"),
            "InProgress"
          )
          mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Right(true))
          mockConnectorToAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(401)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isRight shouldBe true
        }

        "the http call comes back with a status of bad request" in {
          val cgtReference = "XACGTP123456789"
          val taxEnrolmentRequest = TaxEnrolmentRequest(
            "userId-1",
            cgtReference,
            Address.UkAddress("line1", None, None, None, "KO11OK"),
            "InProgress"
          )
          mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Right(true))
          mockConnectorToAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(400)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isRight shouldBe true
        }

        "the http call comes back with any other 5xx non-successful status" in {
          val cgtReference = "XACGTP123456789"
          val taxEnrolmentRequest = TaxEnrolmentRequest(
            "userId-1",
            cgtReference,
            Address.UkAddress("line1", None, None, None, "KO11OK"),
            "InProgress"
          )
          mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Right(true))
          mockConnectorToAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(500)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isLeft shouldBe true
        }

        "the http call comes back with any other 4xx non-successful status" in {
          val cgtReference = "XACGTP123456789"
          val taxEnrolmentRequest = TaxEnrolmentRequest(
            "userId-1",
            cgtReference,
            Address.UkAddress("line1", None, None, None, "KO11OK"),
            "InProgress"
          )
          mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Right(true))
          mockConnectorToAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(429)))
          await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isRight shouldBe true
        }

      }

      "return a tax enrolment created success response" in {
        val cgtReference = "XACGTP123456789"
        val taxEnrolmentRequest = TaxEnrolmentRequest(
          "userId-1",
          cgtReference,
          Address.UkAddress("line1", None, None, None, "KO11OK"),
          "InProgress"
        )

        mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Right(true))
        mockConnectorToAllocateEnrolment(taxEnrolmentRequest)(Right(HttpResponse(204)))
        await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isRight shouldBe true
      }

      "return failure if database insert failed" in {
        val cgtReference = "XACGTP123456789"
        val taxEnrolmentRequest = TaxEnrolmentRequest(
          "userId-1",
          cgtReference,
          Address.UkAddress("line1", None, None, None, "KO11OK"),
          "InProgress"
        )

        mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Left(Error("Mongodb error")))
        await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isLeft shouldBe true
      }
      "return failure if connector failed" in {
        val cgtReference = "XACGTP123456789"
        val taxEnrolmentRequest = TaxEnrolmentRequest(
          "userId-1",
          cgtReference,
          Address.UkAddress("line1", None, None, None, "KO11OK"),
          "InProgress"
        )

        mockRepositoryToAllocateEnrolment(taxEnrolmentRequest)(Right(true))
        mockConnectorToAllocateEnrolment(taxEnrolmentRequest)(Left(Error("Connection failed")))
        await(service.allocateEnrolmentToGroup(taxEnrolmentRequest).value).isLeft shouldBe true
      }
    }
  }
}
