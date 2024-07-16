/*
 * Copyright 2024 HM Revenue & Customs
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

package endpoints

import com.google.inject.{Inject, Singleton}
import play.api.http.HeaderNames
import play.api.mvc.{Action, AnyContent, AnyContentAsEmpty, ControllerComponents, Result}
import play.api.test.FakeRequest
import stubs.AuthStub
import support.IntegrationBaseSpec
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.Future

@Singleton
class DummyController @Inject() (auth: AuthenticateActions, cc: ControllerComponents) extends BackendController(cc) {
  def runAuthTest(): Action[AnyContent] = auth(parse.default).async { _ =>
    Future.successful(Ok(""))
  }
}

class AuthISpec extends IntegrationBaseSpec {

  trait Test {
    val testController: DummyController = app.injector.instanceOf[DummyController]

    implicit lazy val fakeRequest: FakeRequest[AnyContentAsEmpty.type] = FakeRequest()

    lazy val fakeGetRequest: FakeRequest[AnyContentAsEmpty.type] = fakeRequest.withHeaders(
      HeaderNames.AUTHORIZATION -> "Bearer Token",
      "X-Client-Id"             -> "client-Id"
    )
  }

  "DummyController" when {
    "a valid request is made" should {
      "authenticate and return an OK response" in new Test {
        AuthStub.authorised()
        val result: Future[Result] = testController.runAuthTest()(fakeGetRequest)

        status(result)          shouldBe 200
        contentAsString(result) shouldBe ""
      }
    }

    "an error occurs" should {
      "return the appropriate error" in new Test {
        AuthStub.unauthorised()
        val result: Future[Result] = testController.runAuthTest()(fakeGetRequest)

        status(result)          shouldBe 403
        contentAsString(result) shouldBe "Forbidden"
      }
    }
  }

}
