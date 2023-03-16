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

package uk.gov.hmrc.cgtpropertydisposals.controllers.actions

import akka.stream.testkit.NoMaterializer

import java.util.concurrent.TimeUnit
import akka.util.Timeout
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.mvc.{BodyParsers, Results}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, BearerTokenExpired, MissingBearerToken, SessionRecordNotFound}
import uk.gov.hmrc.auth.core.authorise.Predicate
import uk.gov.hmrc.auth.core.retrieve.{Credentials, Retrieval}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AuthenticatedActionsSpec extends AnyWordSpec with Matchers with MockFactory {

  implicit val timeout: Timeout = Timeout(FiniteDuration(5, TimeUnit.SECONDS))

  val executionContext: ExecutionContextExecutor = ExecutionContext.global

  val authConnector = mock[AuthConnector]

  val builder = new AuthenticateActionBuilder(
    authConnector,
    new BodyParsers.Default()(NoMaterializer),
    executionContext
  )

  def mockAuthorise()(response: Future[Option[Credentials]]) =
    (authConnector
      .authorise[Option[Credentials]](_: Predicate, _: Retrieval[Option[Credentials]])(
        _: HeaderCarrier,
        _: ExecutionContext
      ))
      .expects(*, *, *, *)
      .returning(response)

  "AuthenticatedActionsSpec" should {
    "authorized request" in {
      mockAuthorise()(Future.successful(Some(Credentials("ggCredId", "provider-type"))))
      val request  = FakeRequest("POST", "/")
      val response = builder.apply(_ => Results.Ok("ok")).apply(request)
      Helpers.status(response) shouldBe 200
    }

    "forbid when no active session is present" in {
      mockAuthorise()(Future.failed(SessionRecordNotFound()))
      val request  = FakeRequest("POST", "/")
      val response = builder.apply(_ => Results.Ok("ok")).apply(request)
      Helpers.status(response) shouldBe 403
    }

    "forbid when bearer token is missing" in {
      mockAuthorise()(Future.failed(MissingBearerToken()))
      val request  = FakeRequest("POST", "/")
      val response = builder.apply(_ => Results.Ok("ok")).apply(request)
      Helpers.status(response) shouldBe 403
    }

    "forbid when bearer token has expired" in {
      mockAuthorise()(Future.failed(BearerTokenExpired()))
      val request  = FakeRequest("POST", "/")
      val response = builder.apply(_ => Results.Ok("ok")).apply(request)
      Helpers.status(response) shouldBe 403
    }

    "return forbidden if credential is missing" in {
      mockAuthorise()(Future.successful(None))
      val request  = FakeRequest("POST", "/")
      val response = builder.apply(_ => Results.Ok("ok")).apply(request)
      Helpers.status(response) shouldBe 403
    }
  }
}
