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

package uk.gov.hmrc.cgtpropertydisposals.controllers.returns

import cats.data.EitherT
import cats.instances.future._
import org.mockito.ArgumentMatchersSugar.*
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.dms.B64Html
import uk.gov.hmrc.cgtpropertydisposals.models.ids.{CgtReference, NINO, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.CompleteReturn.{CompleteMultipleDisposalsReturn, CompleteSingleDisposalReturn}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeAnswers.CompleteRepresenteeAnswers
import uk.gov.hmrc.cgtpropertydisposals.models.returns.RepresenteeReferenceId.{NoReferenceId, RepresenteeCgtReference, RepresenteeNino, RepresenteeSautr}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{RepresenteeDetails, SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.dms.{DmsSubmissionRequest, DmsSubmissionService}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.{RegisterWithoutIdService, SubscriptionService}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{DraftReturnsService, ReturnsService}
import uk.gov.hmrc.cgtpropertydisposals.util.HtmlSanitizer
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.workitem.WorkItem

import java.time.LocalDateTime
import java.util.{Base64, UUID}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmitReturnsControllerSpec extends ControllerSpec {
  val draftReturnsService: DraftReturnsService      = mock[DraftReturnsService]
  val returnsService: ReturnsService                = mock[ReturnsService]
  val dmsService: DmsSubmissionService              = mock[DmsSubmissionService]
  val subscriptionService: SubscriptionService      = mock[SubscriptionService]
  val registrationService: RegisterWithoutIdService = mock[RegisterWithoutIdService]

  implicit val headerCarrier: HeaderCarrier = HeaderCarrier()
  private val dmsSubmissionRequestWorkItem  = sample[WorkItem[DmsSubmissionRequest]]

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  private def fakeRequestWithJsonBody(body: JsValue) =
    request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller                                     = new SubmitReturnsController(
    authenticate = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    draftReturnsService = draftReturnsService,
    returnsService = returnsService,
    dmsSubmissionService = dmsService,
    cc = Helpers.stubControllerComponents()
  )

  private def mockSubmitReturnService(request: SubmitReturnRequest, representeeDetails: Option[RepresenteeDetails])(
    response: Either[Error, SubmitReturnResponse]
  ) =
    returnsService
      .submitReturn(request, representeeDetails)(*, *)
      .returns(EitherT.fromEither[Future](response))

  private def mockDeleteDraftReturnService(id: UUID)(response: Either[Error, Unit]) =
    draftReturnsService
      .deleteDraftReturns(List(id))
      .returns(EitherT.fromEither[Future](response))

  private def mockDmsRequestEnqueue() =
    dmsService
      .enqueue(*)
      .returns(EitherT.pure(dmsSubmissionRequestWorkItem))

  "SubmitReturnsController" when {
    "handling requests to submit returns" must {
      "return 200 for successful submission" in {
        val expectedResponseBody = sample[SubmitReturnResponse]
        val requestBody          = sample[SubmitReturnRequest].copy(
          checkYourAnswerPageHtml = sample[B64Html],
          completeReturn = sample[CompleteSingleDisposalReturn].copy(representeeAnswers = None)
        )
        mockSubmitReturnService(requestBody, None)(Right(expectedResponseBody))
        mockDmsRequestEnqueue()
        mockDeleteDraftReturnService(requestBody.id)(Right(()))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(expectedResponseBody)
      }

      "pass on the correct representee details if there are representee detail in the request" in {
        def sampleDetails(id: Either[SAUTR, Either[NINO, CgtReference]]): RepresenteeDetails =
          RepresenteeDetails(
            sample[CompleteRepresenteeAnswers].copy(id = id match {
              case Left(sautr)          => RepresenteeSautr(sautr)
              case Right(Left(nino))    => RepresenteeNino(nino)
              case Right(Right(cgtRef)) => RepresenteeCgtReference(cgtRef)
            }),
            id
          )

        List(
          sampleDetails(Left(sample[SAUTR])),
          sampleDetails(Right(Left(sample[NINO]))),
          sampleDetails(Right(Right(sample[CgtReference])))
        ).foreach { representeeDetails =>
          withClue(s"For $representeeDetails: ") {
            val expectedResponseBody = sample[SubmitReturnResponse]
            val representeeAnswers   = representeeDetails.answers
            val requestBody          = sample[SubmitReturnRequest].copy(
              checkYourAnswerPageHtml = sample[B64Html],
              completeReturn = sample[CompleteSingleDisposalReturn].copy(
                representeeAnswers = Some(representeeAnswers)
              )
            )
            mockSubmitReturnService(requestBody, Some(representeeDetails))(Right(expectedResponseBody))
            mockDmsRequestEnqueue()
            mockDeleteDraftReturnService(requestBody.id)(Right(()))

            val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
            status(result)        shouldBe OK
            contentAsJson(result) shouldBe Json.toJson(expectedResponseBody)
          }
        }
      }

      "return 200 for successful submission with sanitized html which contained forbidden elements" in {
        HtmlSanitizer.allowedElements.foreach { allowedElement =>
          HtmlSanitizer.blockedElements.foreach { blockedElement =>
            val htmlWithForbiddenElements =
              s"""<html>
                 |<body>
                 |<h1>My First Heading</h1>
                 |<$allowedElement>Sample value</$allowedElement>
                 |<$blockedElement>Sample value</$blockedElement>
                 |<p>My first paragraph.</p>
                 |</body>
                 |</html>
                 |""".stripMargin

            val expectedResponseBody = sample[SubmitReturnResponse]

            val requestBodyWithForbiddenElements = sample[SubmitReturnRequest].copy(
              completeReturn = sample[CompleteMultipleDisposalsReturn].copy(representeeAnswers = None),
              checkYourAnswerPageHtml =
                B64Html(new String(Base64.getEncoder.encode(htmlWithForbiddenElements.getBytes())))
            )

            mockSubmitReturnService(requestBodyWithForbiddenElements, None)(Right(expectedResponseBody))
            mockDmsRequestEnqueue()
            mockDeleteDraftReturnService(requestBodyWithForbiddenElements.id)(Right(()))

            val result =
              controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBodyWithForbiddenElements)))
            status(result)        shouldBe OK
            contentAsJson(result) shouldBe Json.toJson(expectedResponseBody)
          }
        }
      }

      "return 500 when des call fails" in {
        val requestBody = sample[SubmitReturnRequest].copy(
          completeReturn = sample[CompleteSingleDisposalReturn].copy(representeeAnswers = None),
          checkYourAnswerPageHtml = sample[B64Html]
        )

        mockSubmitReturnService(requestBody, None)(Left(Error.apply("error while submitting return to DES")))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return 500 when deleting draft return fails" in {
        val requestBody = sample[SubmitReturnRequest].copy(
          completeReturn = sample[CompleteMultipleDisposalsReturn].copy(representeeAnswers = None),
          checkYourAnswerPageHtml = sample[B64Html]
        )
        mockSubmitReturnService(requestBody, None)(Right(sample[SubmitReturnResponse]))
        mockDmsRequestEnqueue()
        mockDeleteDraftReturnService(requestBody.id)(Left(Error.apply("error while deleting draft return")))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

      "return a 500" when {
        "there is no reference id for a representee" in {
          val requestBody = sample[SubmitReturnRequest].copy(
            checkYourAnswerPageHtml = sample[B64Html],
            completeReturn = sample[CompleteSingleDisposalReturn].copy(
              representeeAnswers = Some(
                sample[CompleteRepresenteeAnswers].copy(id = NoReferenceId)
              )
            )
          )
          val result      = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }
  }
}
