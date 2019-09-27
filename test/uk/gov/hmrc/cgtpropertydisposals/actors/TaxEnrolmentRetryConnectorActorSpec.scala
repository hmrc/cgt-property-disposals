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

package uk.gov.hmrc.cgtpropertydisposals.actors

import java.time.LocalDateTime

import org.scalacheck.ScalacheckShapeless._
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import cats.data.EitherT
import org.scalacheck.{Arbitrary, Gen}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryConnectorActor.RetryCallToTaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.actors.TaxEnrolmentRetryManager.{MarkTaxEnrolmentRetryRequestAsSuccess, RetryTaxEnrolmentRequest}
import uk.gov.hmrc.cgtpropertydisposals.models.{TaxEnrolmentError, TaxEnrolmentRequest, sample}
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse
import uk.gov.hmrc.cgtpropertydisposals.service.TaxEnrolmentService.TaxEnrolmentResponse.{TaxEnrolmentCreated, TaxEnrolmentFailedForSomeOtherReason}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.Future

class TaxEnrolmentRetryConnectorActorSpec
    extends TestKit(ActorSystem())
    with ImplicitSender
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll
    with MockFactory {

  override def afterAll: Unit =
    TestKit.shutdownActorSystem(system)

  val mockTaxEnrolmentService = mock[TaxEnrolmentService]

  val taxEnrolmentRetryConnectorActor = system.actorOf(TaxEnrolmentRetryConnectorActor.props(mockTaxEnrolmentService))
  val http4xxResponse                 = HttpResponse(403, None, Map.empty, None)
  val http5xxResponse                 = HttpResponse(502, None, Map.empty, None)

  def mockAllocateEnrolmentToGroup(taxEnrolmentRequest: TaxEnrolmentRequest)(
    response: Either[TaxEnrolmentError, TaxEnrolmentResponse]
  ) =
    (mockTaxEnrolmentService
      .allocateEnrolmentToGroup(_: TaxEnrolmentRequest)(_: HeaderCarrier))
      .expects(taxEnrolmentRequest, *)
      .returning(EitherT(Future.successful(response)))

  implicit val localDateArb: Arbitrary[LocalDateTime] = Arbitrary.apply[LocalDateTime](Gen.const(LocalDateTime.now()))
  val taxEnrolmentRequest                             = sample[TaxEnrolmentRequest]

  "A Tax Enrolment Connector actor" must {
    "reply back with a message to retry when the tax enrolment call fails" in {
      mockAllocateEnrolmentToGroup(taxEnrolmentRequest)(Left(TaxEnrolmentError(taxEnrolmentRequest)))
      taxEnrolmentRetryConnectorActor ! RetryCallToTaxEnrolmentService(taxEnrolmentRequest)
      expectMsg(RetryTaxEnrolmentRequest(taxEnrolmentRequest))
    }

    "reply back with a message to mark the tax enrolment request as successful when the tax enrolment call succeeds" in {
      mockAllocateEnrolmentToGroup(taxEnrolmentRequest)(Right(TaxEnrolmentCreated))
      taxEnrolmentRetryConnectorActor ! RetryCallToTaxEnrolmentService(taxEnrolmentRequest)
      expectMsg(MarkTaxEnrolmentRetryRequestAsSuccess(taxEnrolmentRequest))
    }

    "reply back with a message to retry if the tax enrolment call returned with a 4xx message" in {
      mockAllocateEnrolmentToGroup(taxEnrolmentRequest)(
        Right(TaxEnrolmentFailedForSomeOtherReason(taxEnrolmentRequest, http4xxResponse))
      )
      taxEnrolmentRetryConnectorActor ! RetryCallToTaxEnrolmentService(taxEnrolmentRequest)
      expectNoMessage()
    }

    "reply back with a message to retry if the tax enrolment call returned with a 5xx message" in {
      mockAllocateEnrolmentToGroup(taxEnrolmentRequest)(
        Right(TaxEnrolmentFailedForSomeOtherReason(taxEnrolmentRequest, http5xxResponse))
      )
      taxEnrolmentRetryConnectorActor ! RetryCallToTaxEnrolmentService(taxEnrolmentRequest)
      expectMsg(RetryTaxEnrolmentRequest(taxEnrolmentRequest))
    }
  }
}
