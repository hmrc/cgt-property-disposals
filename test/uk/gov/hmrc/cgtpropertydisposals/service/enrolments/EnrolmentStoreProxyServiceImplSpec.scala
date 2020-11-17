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

package uk.gov.hmrc.cgtpropertydisposals.service.enrolments

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.HttpSupport
import uk.gov.hmrc.cgtpropertydisposals.connectors.enrolments.EnrolmentStoreProxyConnector
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EnrolmentStoreProxyServiceImplSpec extends WordSpec with Matchers with MockFactory with HttpSupport {

  val mockEnrolmentProxyConnector = mock[EnrolmentStoreProxyConnector]

  val service = new EnrolmentStoreProxyServiceImpl(mockEnrolmentProxyConnector)

  def mockGetPrincipalEnrolments(cgtReference: CgtReference)(response: Either[Error, HttpResponse]) =
    (mockEnrolmentProxyConnector
      .getPrincipalEnrolments(_: CgtReference)(_: HeaderCarrier))
      .expects(cgtReference, *)
      .returning(EitherT.fromEither[Future](response))

  "EnrolmentStoreProxyServiceImpl" when {

    "handling requests to determine whether a cgt enrolment exists for a cgt reference" must {

      implicit val hc: HeaderCarrier = HeaderCarrier()

      val cgtReference = sample[CgtReference]

      "return true if the call comes back with status 200 (OK)" in {
        mockGetPrincipalEnrolments(cgtReference)(Right(HttpResponse(200, "")))

        await(service.cgtEnrolmentExists(cgtReference).value) shouldBe Right(true)
      }

      "return false if the call comes back with status 204 (No Content)" in {
        mockGetPrincipalEnrolments(cgtReference)(Right(HttpResponse(204, "")))

        await(service.cgtEnrolmentExists(cgtReference).value) shouldBe Right(false)
      }

      "return an error" when {

        "the call is not successful" in {
          mockGetPrincipalEnrolments(cgtReference)(Left(Error("")))

          await(service.cgtEnrolmentExists(cgtReference).value).isLeft shouldBe true
        }

        "the call is successful but an unexpected status code is returned" in {
          mockGetPrincipalEnrolments(cgtReference)(Right(HttpResponse(500, "")))

          await(service.cgtEnrolmentExists(cgtReference).value).isLeft shouldBe true
        }

      }

    }

  }

}
