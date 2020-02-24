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
import java.util.UUID

import akka.stream.Materializer
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Headers
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{SubmitReturnRequest, SubmitReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{CompleteReturnsService, DraftReturnsService}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SubmitReturnsControllerSpec extends ControllerSpec {

  val draftReturnsService: DraftReturnsService = mock[DraftReturnsService]
  val completeReturnsService: CompleteReturnsService = mock[CompleteReturnsService]
  val auditService: AuditService = mock[AuditService]

  implicit val headerCarrier = HeaderCarrier()
  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new SubmitReturnsController(
    authenticate        = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    draftReturnsService = draftReturnsService,
    completeReturnsService = completeReturnsService,
    auditService        = auditService,
    cc                  = Helpers.stubControllerComponents()
  )

  def mockSubmitReturnService(request: SubmitReturnRequest)(response: Either[Error, SubmitReturnResponse]) =
    (completeReturnsService
      .submitReturn(_: SubmitReturnRequest)(_: HeaderCarrier))
      .expects(request, *)
      .returning(EitherT.fromEither[Future](response))

  def mockDeleteDraftReturnService(response: Either[Error, Int]) =
    (draftReturnsService
      .deleteDraftReturn(_: UUID))
      .expects(*)
      .returning(EitherT.fromEither[Future](response))

  "SubmitReturnsController" when {

    "handling requests to submit returns" must {

      "return 200 for successful submission" in {
        val expectedResponseBody = sample[SubmitReturnResponse]
        val requestBody = sample[SubmitReturnRequest]

        mockDeleteDraftReturnService(Right(1))
        mockSubmitReturnService(requestBody)(Right(expectedResponseBody))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result)        shouldBe OK
        contentAsJson(result) shouldBe Json.toJson(expectedResponseBody)
      }

      "return 500 when des call fails" in {
        val requestBody = sample[SubmitReturnRequest]

        mockSubmitReturnService(requestBody)(Left(Error.apply("error while submitting return to DES")))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result)        shouldBe INTERNAL_SERVER_ERROR
      }

      "return 500 when deleting draft return fails" in {
        val requestBody = sample[SubmitReturnRequest]

        mockDeleteDraftReturnService(Left(Error.apply("error while deleting draft return")))
        mockSubmitReturnService(requestBody)(Right(sample[SubmitReturnResponse]))

        val result = controller.submitReturn()(fakeRequestWithJsonBody(Json.toJson(requestBody)))
        status(result)        shouldBe INTERNAL_SERVER_ERROR
      }
    }
  }
}
