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

package uk.gov.hmrc.cgtpropertydisposals.controllers.actions

import java.time.LocalDateTime

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.mvc._
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve._
import uk.gov.hmrc.auth.core.{AuthConnector, AuthorisedFunctions, _}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendHeaderCarrierProvider

import scala.concurrent.{ExecutionContext, Future}

final case class AuthenticatedUser(ggCredId: String)
class AuthenticatedRequest[+A](
  val user: AuthenticatedUser,
  val timestamp: LocalDateTime,
  val headerCarrier: HeaderCarrier,
  request: Request[A]
) extends WrappedRequest[A](request)

@ImplementedBy(classOf[AuthenticateActionBuilder])
trait AuthenticateActions extends ActionBuilder[AuthenticatedRequest, AnyContent]

class AuthenticateActionBuilder @Inject() (
  val authConnector: AuthConnector,
  val parser: BodyParsers.Default,
  val executionContext: ExecutionContext
) extends AuthenticateActions
    with AuthorisedFunctions
    with BackendHeaderCarrierProvider {

  override def invokeBlock[A](request: Request[A], block: AuthenticatedRequest[A] => Future[Result]): Future[Result] = {
    val forbidden = Results.Forbidden("Forbidden")
    val carrier   = hc(request)
    authorised(AuthProviders(GovernmentGateway))
      .retrieve(v2.Retrievals.credentials) {
        case Some(credentials) =>
          val user = AuthenticatedUser(credentials.providerId)
          block(new AuthenticatedRequest[A](user, LocalDateTime.now(), carrier, request))
        case _                 => Future.successful(forbidden)
      }(carrier, executionContext)
      .recover { case _: NoActiveSession =>
        forbidden
      }(executionContext)
  }
}
