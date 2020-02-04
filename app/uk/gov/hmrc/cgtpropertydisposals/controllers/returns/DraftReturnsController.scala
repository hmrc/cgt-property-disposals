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

import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{DraftReturn, GetDraftReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.AuditService
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DraftReturnsService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

class DraftReturnsController @Inject() (
  authenticate: AuthenticateActions,
  draftReturnsService: DraftReturnsService,
  auditService: AuditService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def draftReturns(cgtReference: String): Action[AnyContent] = authenticate.async {
    draftReturnsService
      .getDraftReturn(cgtReference)
      .value
      .map {
        case Left(e) =>
          logger.warn(s"Failed to get draftreturn with $cgtReference", e)
          InternalServerError
        case Right(drList) =>
          Ok(Json.toJson(GetDraftReturnResponse(drList)))
      }
  }

  def draftReturnSubmit: Action[JsValue] = authenticate(parse.json).async { implicit request =>
    withJsonBody[DraftReturn] { draftReturn =>
      draftReturnsService.saveDraftReturn(draftReturn).value.map {
        case Left(e) =>
          logger.warn("Could not store draft return", e)
          InternalServerError
        case Right(_) =>
          Ok
      }
    }
  }

}