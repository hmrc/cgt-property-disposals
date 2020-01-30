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

import akka.stream.Materializer
import cats.data.EitherT
import cats.instances.future._
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.Headers
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.cgtpropertydisposals.Fake
import uk.gov.hmrc.cgtpropertydisposals.controllers.ControllerSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticatedRequest
import uk.gov.hmrc.cgtpropertydisposals.models.Generators.sample
import uk.gov.hmrc.cgtpropertydisposals.models.returns.DraftReturn
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DraftReturnsService
import uk.gov.hmrc.http.HeaderCarrier
import play.api.test.Helpers._
import uk.gov.hmrc.cgtpropertydisposals.models.Error

import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.cgtpropertydisposals.models.Generators._

import scala.concurrent.{ExecutionContext, Future}

class DraftReturnsControllerSpec extends ControllerSpec {

  val draftReturnsService = mock[DraftReturnsService]
  val auditService        = mock[AuditService]

  implicit val headerCarrier = HeaderCarrier()

  val draftReturn = sample[DraftReturn]

  def mockDraftReturnsService(df: DraftReturn)(response: Either[Error, Unit]) =
    (draftReturnsService
      .saveDraftReturn(_: DraftReturn)(_: HeaderCarrier, _: ExecutionContext))
      .expects(df, *, *)
      .returning(EitherT.fromEither[Future](response))

  implicit lazy val mat: Materializer = fakeApplication.materializer

  val request = new AuthenticatedRequest(
    Fake.user,
    LocalDateTime.now(),
    headerCarrier,
    FakeRequest()
  )

  def fakeRequestWithJsonBody(body: JsValue) = request.withHeaders(Headers.apply(CONTENT_TYPE -> JSON)).withBody(body)

  val controller = new DraftReturnsController(
    authenticate        = Fake.login(Fake.user, LocalDateTime.of(2020, 1, 1, 15, 47, 20)),
    draftReturnsService = draftReturnsService,
    auditService        = auditService,
    cc                  = Helpers.stubControllerComponents()
  )

  "DraftReturnsController" when {

    "Draft returns" must {

      "store in mongo successfully" in {
        mockDraftReturnsService(draftReturn)(Right(()))

        val result = controller.draftReturnSubmit()(fakeRequestWithJsonBody(Json.toJson(draftReturn)))
        status(result) shouldBe OK
      }

      "should not store in mongo" in {
        List(
          Error(new Exception("oh no!")),
          Error("oh no!")
        ).foreach { e =>
          mockDraftReturnsService(draftReturn)(Left(e))

          val result = controller.draftReturnSubmit()(fakeRequestWithJsonBody(Json.toJson(draftReturn)))
          status(result) shouldBe INTERNAL_SERVER_ERROR
        }
      }
    }

  }

}
