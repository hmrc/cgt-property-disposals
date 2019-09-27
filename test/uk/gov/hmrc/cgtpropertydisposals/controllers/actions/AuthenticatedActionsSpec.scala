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

package uk.gov.hmrc.cgtpropertydisposals.controllers.actions

import akka.util.Timeout
import org.mockito.{MockitoSugar, _}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}
import play.api.mvc.{BodyParsers, Results}
import play.api.test.{FakeRequest, Helpers, NoMaterializer}
import uk.gov.hmrc.auth.core.retrieve.{Credentials, ~}
import uk.gov.hmrc.auth.core.{AuthConnector, BearerTokenExpired, MissingBearerToken, SessionRecordNotFound}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

class AuthenticatedActionsSpec
    extends FlatSpec
    with Matchers
    with MockitoSugar
    with ScalaFutures
    with ArgumentMatchersSugar {

  implicit val timeout: Timeout = Timeout(patienceConfig.timeout)

  val executionContext: ExecutionContextExecutor = ExecutionContext.global

  val authConnector = mock[AuthConnector]

  val builder = new AuthenticateActionBuilder(
    authConnector,
    new BodyParsers.Default()(NoMaterializer),
    executionContext
  )

  it should "authorized request" in {
    when(authConnector.authorise[Option[Credentials]](*, *)(*, *)) thenReturn Future.successful(
      Some(Credentials("cred-id", "provide-type"))
    )
    val request  = FakeRequest("POST", "/")
    val response = builder.apply(_ => Results.Ok("ok")).apply(request)
    Helpers.status(response) shouldBe 200
  }

  it should "forbid when no active session is present" in {
    when(authConnector.authorise[Option[Credentials]](*, *)(*, *)) thenReturn Future.failed(
      SessionRecordNotFound()
    )
    val request  = FakeRequest("POST", "/")
    val response = builder.apply(_ => Results.Ok("ok")).apply(request)
    Helpers.status(response) shouldBe 403
  }

  it should "forbid when bearer token is missing" in {
    when(authConnector.authorise[Option[Credentials]](*, *)(*, *)) thenReturn Future.failed(
      MissingBearerToken()
    )
    val request  = FakeRequest("POST", "/")
    val response = builder.apply(_ => Results.Ok("ok")).apply(request)
    Helpers.status(response) shouldBe 403
  }

  it should "forbid when bearer token has expired" in {
    when(authConnector.authorise[Option[Credentials]](*, *)(*, *)) thenReturn Future.failed(
      BearerTokenExpired()
    )
    val request  = FakeRequest("POST", "/")
    val response = builder.apply(_ => Results.Ok("ok")).apply(request)
    Helpers.status(response) shouldBe 403
  }

  it should "return forbidden if credential is missing" in {
    when(authConnector.authorise[Option[String]](*, *)(*, *)) thenReturn Future.successful(
      None
    )
    val request  = FakeRequest("POST", "/")
    val response = builder.apply(_ => Results.Ok("ok")).apply(request)
    Helpers.status(response) shouldBe 403
  }
}
