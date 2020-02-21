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

package uk.gov.hmrc.cgtpropertydisposals.service.returns

import java.time.LocalDate

import cats.data.EitherT
import cats.instances.future._
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.returns.SubmitReturnsConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.{AmountInPence, Error}
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class CompleteReturnsServiceSpec extends WordSpec with Matchers with MockFactory {

  val submitReturnsConnector = mock[SubmitReturnsConnector]
  val draftReturnsService    = new DefaultCompleteReturnsService(submitReturnsConnector, MockMetrics.metrics)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def mockSubmitReturn(returnRequest: SubmitReturnRequest)(response: Either[Error, HttpResponse]) =
    (submitReturnsConnector
      .submit(_: SubmitReturnRequest)(_: HeaderCarrier))
      .expects(returnRequest, hc)
      .returning(EitherT.fromEither[Future](response))

  "CompleteReturnsService" when {
    val submitReturnResponse = SubmitReturnResponse(
      chargeReference = "XCRG9448959757",
      amount          = AmountInPence.fromPounds(11.0d),
      dueDate         = LocalDate.parse("2020-03-11"),
      formBundleId    = "804123737752"
    )

    "first return" should {
      val jsonBody =
        """
          |{
          |"processingDate":"2020-02-20T09:30:47Z",
          |"ppdReturnResponseDetails": {
          |     "chargeType": "Late Penalty",
          |     "chargeReference":"XCRG9448959757",
          |     "amount":11.0,
          |     "dueDate":"2020-03-11",
          |     "formBundleNumber":"804123737752",
          |     "cgtReferenceNumber":"XLCGTP212487578"
          |  }
          |}
          |""".stripMargin

      "submitted successfully with 200 status" in {
        val submitReturnRequest = sample[SubmitReturnRequest]
        mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(200, Some(Json.parse(jsonBody)))))
        await(draftReturnsService.submitReturn(submitReturnRequest).value) shouldBe Right(submitReturnResponse)
      }

      "fail to submit complete return" in {
        val submitReturnRequest = sample[SubmitReturnRequest]
        mockSubmitReturn(submitReturnRequest)(
          Left(Error("call to submit return came back with status ${response.status}"))
        )
        await(draftReturnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
      }

      "the http call comes back with a status other than 200" in {
        val submitReturnRequest = sample[SubmitReturnRequest]
        mockSubmitReturn(submitReturnRequest)(Right(HttpResponse(500)))
        await(draftReturnsService.submitReturn(submitReturnRequest).value).isLeft shouldBe true
      }
    }

  }
}
