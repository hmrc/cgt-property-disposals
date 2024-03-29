/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.service.email

import cats.data.EitherT
import cats.instances.future._
import org.mockito.ArgumentMatchersSugar.*
import org.mockito.IdiomaticMockito
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.Request
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.connectors.EmailConnector
import uk.gov.hmrc.cgtpropertydisposals.metrics.MockMetrics
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.name.ContactName
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.audit.SubscriptionConfirmationEmailSentEvent
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.subscription.SubscribedDetails
import uk.gov.hmrc.cgtpropertydisposals.models.returns.audit.ReturnConfirmationEmailSentEvent
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.models.{Email, Error}
import uk.gov.hmrc.cgtpropertydisposals.service.audit.AuditService
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class EmailServiceImplSpec extends AnyWordSpec with Matchers with IdiomaticMockito {

  val mockAuditService: AuditService = mock[AuditService]

  private val mockEmailConnector = mock[EmailConnector]

  val service = new EmailServiceImpl(mockEmailConnector, mockAuditService, MockMetrics.metrics)

  implicit val hc: HeaderCarrier = HeaderCarrier()

  implicit val request: Request[_] = FakeRequest()

  private def mockSendSubscriptionConfirmationEmail(cgtReference: CgtReference, email: Email, contactName: ContactName)(
    result: Either[Error, HttpResponse]
  ) =
    mockEmailConnector
      .sendSubscriptionConfirmationEmail(cgtReference, email, contactName)(*)
      .returns(EitherT.fromEither[Future](result))

  private def mockSendReturnConfirmationEmail(
    submitReturnResponse: SubmitReturnResponse,
    subscribedDetails: SubscribedDetails
  )(
    result: Either[Error, HttpResponse]
  ) =
    mockEmailConnector
      .sendReturnSubmitConfirmationEmail(submitReturnResponse, subscribedDetails)(*)
      .returns(EitherT.fromEither[Future](result))

  private def mockAuditSubscriptionEmailEvent(email: String, cgtReference: String) =
    mockAuditService
      .sendEvent(
        "subscriptionConfirmationEmailSent",
        SubscriptionConfirmationEmailSentEvent(
          email,
          cgtReference
        ),
        "subscription-confirmation-email-sent"
      )(*, *, *)
      .doesNothing()

  private def mockAuditReturnConfirmationEmailEvent(email: String, cgtReference: String, submissionId: String) =
    mockAuditService
      .sendEvent(
        "returnConfirmationEmailSent",
        ReturnConfirmationEmailSentEvent(
          email,
          cgtReference,
          submissionId
        ),
        "return-confirmation-email-sent"
      )(*, *, *)
      .doesNothing()

  "EmailServiceImpl" when {
    "handling requests to send subscription confirmation emails" must {
      val (cgtReference, email, contactName) =
        (sample[CgtReference], sample[Email], sample[ContactName])

      "return an error" when {
        "the http status of the response is not 202 (accepted)" in {
          mockSendSubscriptionConfirmationEmail(cgtReference, email, contactName)(Right(HttpResponse(200, "")))

          await(service.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value).isLeft shouldBe true
        }

        "the call to send the email fails" in {
          mockSendSubscriptionConfirmationEmail(cgtReference, email, contactName)(Left(Error("")))

          await(service.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value).isLeft shouldBe true
        }
      }

      "return a successful response" when {
        "the http status of the response is 202 (accepted)" in {
          mockSendSubscriptionConfirmationEmail(cgtReference, email, contactName)(Right(HttpResponse(202, "")))
          mockAuditSubscriptionEmailEvent(email.value, cgtReference.value)

          await(service.sendSubscriptionConfirmationEmail(cgtReference, email, contactName).value) shouldBe Right(())
        }
      }
    }

    "handling requests to send return submission confirmation emails" must {
      val (submitReturnRequest, submitReturnResponse) =
        sample[SubmitReturnRequest] -> sample[SubmitReturnResponse]

      "return an error" when {
        "the http status of the response is not 202 (accepted)" in {
          mockSendReturnConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
            Right(HttpResponse(200, ""))
          )

          await(
            service.sendReturnConfirmationEmail(submitReturnRequest, submitReturnResponse).value
          ).isLeft shouldBe true
        }

        "the call to send the email fails" in {
          mockSendReturnConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(Left(Error("")))

          await(
            service.sendReturnConfirmationEmail(submitReturnRequest, submitReturnResponse).value
          ).isLeft shouldBe true
        }
      }

      "return a successful response" when {
        "the http status of the response is 202 (accepted)" in {
          mockSendReturnConfirmationEmail(submitReturnResponse, submitReturnRequest.subscribedDetails)(
            Right(HttpResponse(202, ""))
          )
          mockAuditReturnConfirmationEmailEvent(
            submitReturnRequest.subscribedDetails.emailAddress.value,
            submitReturnRequest.subscribedDetails.cgtReference.value,
            submitReturnResponse.formBundleId
          )

          await(
            service.sendReturnConfirmationEmail(submitReturnRequest, submitReturnResponse).value
          ) shouldBe Right(())
        }
      }
    }
  }
}
