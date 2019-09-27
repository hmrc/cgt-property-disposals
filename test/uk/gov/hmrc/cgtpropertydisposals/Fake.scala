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

package uk.gov.hmrc.cgtpropertydisposals

import java.time.LocalDateTime

import play.api.mvc._
import play.api.test.Helpers
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.{AuthenticateActions, AuthenticatedRequest, AuthenticatedUser}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.{ExecutionContext, Future}

object Fake {

  def login(
    user: AuthenticatedUser,
    timestamp: LocalDateTime,
    hc: HeaderCarrier = HeaderCarrier()
  ): AuthenticateActions =
    new AuthenticateActions {
      override def parser: BodyParser[AnyContent] = Helpers.stubBodyParser()

      override def invokeBlock[A](
        request: Request[A],
        block: AuthenticatedRequest[A] => Future[Result]
      ): Future[Result] =
        block(new AuthenticatedRequest(user, timestamp, hc, request))

      override protected def executionContext: ExecutionContext = ExecutionContext.global
    }

  val user: AuthenticatedUser = AuthenticatedUser("user-cred-id")

}
