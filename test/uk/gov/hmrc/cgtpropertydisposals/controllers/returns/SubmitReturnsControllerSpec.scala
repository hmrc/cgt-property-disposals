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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import java.time.LocalDateTime
import java.util.{Base64, UUID}

import akka.stream.Materializer
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Headers, Request}
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.{B64Html, EnvelopeId}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{CompleteReturn, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.DmsSubmissionService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{DraftReturnsService, ReturnsService}
import uk.gov.hmrc.cgtpropertydisposals.util.HtmlSanitizer
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmitReturnsControllerSpec extends ControllerSpec {

  val draftReturnsService: DraftReturnsService = mock[DraftReturnsService]
  val returnsService: ReturnsService           = mock[ReturnsService]
  val dmsService: DmsSubmissionService         = mock[DmsSubmissionService]

  implicit val headerCarrier          = HeaderCarrier()
  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new SubmitReturnsController(
    authenticate         = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    draftReturnsService  = draftReturnsService,
    returnsService       = returnsService,
    dmsSubmissionService = dmsService,
    cc                   = Helpers.stubControllerComponents()
  )

  def mockSubmitReturnService(request: SubmitReturnRequest)(response: Either[Error, SubmitReturnResponse]) =
    (returnsService
      .submitReturn(_: SubmitReturnRequest)(_: HeaderCarrier, _: Request[_]))
      .expects(request, *, *)
      .returning(EitherT.fromEither[Future](response))

  def mockDeleteDraftReturnService(id: UUID)(response: Either[Error, Unit]) =
    (draftReturnsService
      .deleteDraftReturns(_: List[UUID]))
      .expects(List(id))
      .returning(EitherT.fromEither[Future](response))

  def mockSubmitToDms() =
    (dmsService
      .submitToDms(_: B64Html, _: String, _: CgtReference, _: CompleteReturn)(_: HeaderCarrier))
      .expects(*, *, *, *, *)
      .returning(EitherT.pure(EnvelopeId("envelope")))

  def mockSubmitSanitizedToDms(submitReturnRequest: SubmitReturnRequest) =
    (dmsService
      .submitToDms(_: B64Html, _: String, _: CgtReference, _: CompleteReturn)(_: HeaderCarrier))
      .expects(*, *, *, submitReturnRequest.completeReturn, *)
      .returning(EitherT.pure(EnvelopeId("envelope")))

  "SubmitReturnsController" when {

    "handling requests to submit returns" must {

      "return 200 for successful submission" in {
        val expectedResponseBody = sample[SubmitReturnResponse]
        val requestBody          = sample[SubmitReturnRequest].copy(checkYourAnswerPageHtml = B64Html(""))

        inSequence {
          mockSubmitReturnService(requestBody)(Right(expectedResponseBody))
          mockSubmitToDms()
          mockDeleteDraftReturnService(requestBody.id)(Right(()))
        }

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(expectedResponseBody)
      }

      "return 200 for successful submission with sanitized html which contained forbidden elements" in {
        val htmlWithForbiddenElements = s"""<html>
                                          |<body>
                                          |<h1>My First Heading</h1>
                                          |<${HtmlSanitizer.allowedElements.head}>Sample value</${HtmlSanitizer.allowedElements.head}>
                                          |<${HtmlSanitizer.blockedElements.head}>Sample value</${HtmlSanitizer.blockedElements.head}>
                                          |<p>My first paragraph.</p>
                                          |</body>
                                          |</html>
                                          |""".stripMargin

        val sanitizedHtml = s"""<html>
                                           |<body>
                                           |<h1>My First Heading</h1>
                                           |<${HtmlSanitizer.allowedElements.head}>Sample value</${HtmlSanitizer.allowedElements.head}>
                                           |<p>My first paragraph.</p>
                                           |</body>
                                           |</html>
                                           |""".stripMargin

        val expectedResponseBody = sample[SubmitReturnResponse]
        val requestBodyWithForbiddenElements = sample[SubmitReturnRequest].copy(checkYourAnswerPageHtml = B64Html(new String(Base64.getEncoder.encode(htmlWithForbiddenElements.getBytes()))))
        val sanitizedRequestBody = requestBodyWithForbiddenElements.copy(checkYourAnswerPageHtml = B64Html(new String(Base64.getEncoder.encode(sanitizedHtml.getBytes()))))

        inSequence {
          mockSubmitReturnService(requestBodyWithForbiddenElements)(Right(expectedResponseBody))
          mockSubmitSanitizedToDms(sanitizedRequestBody)
          mockDeleteDraftReturnService(requestBodyWithForbiddenElements.id)(Right(()))
        }

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBodyWithForbiddenElements)))
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(expectedResponseBody)
      }

      "return 500 when des call fails" in {
        val requestBody = sample[SubmitReturnRequest].copy(checkYourAnswerPageHtml = B64Html(""))

        mockSubmitReturnService(requestBody)(Left(Error.apply("error while submitting return to DES")))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return 500 when deleting draft return fails" in {
        val requestBody = sample[SubmitReturnRequest].copy(checkYourAnswerPageHtml = B64Html(""))

        inSequence {
          mockSubmitReturnService(requestBody)(Right(sample[SubmitReturnResponse]))
          mockSubmitToDms()
          mockDeleteDraftReturnService(requestBody.id)(Left(Error.apply("error while deleting draft return")))
        }

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
