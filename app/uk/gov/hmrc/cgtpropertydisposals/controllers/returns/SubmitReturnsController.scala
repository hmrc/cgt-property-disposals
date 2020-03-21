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

import cats.instances.future._
import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.returns.SubmitReturnRequest
import uk.gov.hmrc.cgtpropertydisposals.service.returns.{DraftReturnsService, ReturnsService}
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

@Singleton
class SubmitReturnsController @Inject() (
  authenticate: AuthenticateActions,
  draftReturnsService: DraftReturnsService,
  returnsService: ReturnsService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def submitReturn: Action[JsValue] = authenticate(parse.json).async { implicit request =>
    withJsonBody[SubmitReturnRequest] { returnRequest =>
      val result =
        for {
          submissionResult <- returnsService.submitReturn(returnRequest)
          _                <- draftReturnsService.deleteDraftReturns(List(returnRequest.id))
        } yield submissionResult

      result.fold(
        { e =>
          logger.warn("Could not submit return", e)
          InternalServerError
        },
        s => Ok(Json.toJson(s))
      )
    }
  }

}
