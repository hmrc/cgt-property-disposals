/*
 * Copyright 2022 HM Revenue & Customs
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

package uk.gov.hmrc.cgtpropertydisposals.controllers.testonly

import java.time.LocalDateTime
import java.util.UUID

import akka.stream.Materializer
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Headers
import play.api.test.Helpers.{CONTENT_TYPE, status, _}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.controllers.returns.DraftReturnsController.DeleteDraftReturnsRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.{sample, _}
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DraftReturnsService
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestOnlyControllerSpec extends ControllerSpec {

  val draftReturnsService = mock[DraftReturnsService]

  implicit val headerCarrier = HeaderCarrier()

  val draftReturn = sample[DraftReturn]

  def mockDeleteDraftReturnService(cgtReference: CgtReference)(response: Either[Error, Unit]) =
    (draftReturnsService
      .deleteDraftReturn(_: CgtReference))
      .expects(cgtReference)
      .returning(EitherT.fromEither[Future](response))

  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  val controller = new TestOnlyController(
    draftReturnsService = draftReturnsService,
    cc = Helpers.stubControllerComponents()
  )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  "TestOnlyController" when {

    "handling requests to delete a draft return" must {

      "return a 200 response if the deletion is successful" in {
        val uuid         = UUID.randomUUID()
        val cgtReference = CgtReference(uuid.toString)
        mockDeleteDraftReturnService(cgtReference)(Right(()))

        val result =
          controller.deleteDraftReturn(cgtReference.value)(
            fakeRequestWithJsonBody(Json.toJson(DeleteDraftReturnsRequest(List(uuid))))
          )
        status(result) shouldBe OK
      }

      "return a 500 response if the deletion is unsuccessful" in {
        val uuid         = UUID.randomUUID()
        val cgtReference = CgtReference(uuid.toString)
        mockDeleteDraftReturnService(cgtReference)(Left(Error("")))

        val result =
          controller.deleteDraftReturn(cgtReference.value)(
            fakeRequestWithJsonBody(Json.toJson(DeleteDraftReturnsRequest(List(uuid))))
          )
        status(result) shouldBe INTERNAL_SERVER_ERROR
      }

    }

  }

}
