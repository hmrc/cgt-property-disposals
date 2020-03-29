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

import java.util.UUID

import com.google.inject.{Inject, Singleton}
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.ids.CgtReference
import uk.gov.hmrc.cgtpropertydisposals.models.returns.{DraftReturn, GetDraftReturnResponse}
import uk.gov.hmrc.cgtpropertydisposals.service.returns.DraftReturnsService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging.LoggerOps
import uk.gov.hmrc.play.bootstrap.controller.BackendController
import cats.instances.future._
import uk.gov.hmrc.cgtpropertydisposals.controllers.returns.DraftReturnsController.DeleteDraftReturnsRequest

import scala.concurrent.ExecutionContext

@Singleton
class DraftReturnsController @Inject() (
  authenticate: AuthenticateActions,
  draftReturnsService: DraftReturnsService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  def draftReturns(cgtReference: String): Action[AnyContent] = Action.async {
    draftReturnsService
      .getDraftReturn(CgtReference(cgtReference))
      .fold(
        { e =>
          logger.warn(s"Failed to get draft return with $cgtReference", e)
          InternalServerError
        },
        draftReturns => Ok(Json.toJson(GetDraftReturnResponse(draftReturns)))
      )
  }

  def storeDraftReturn(cgtReference: String): Action[JsValue] = authenticate(parse.json).async { implicit request =>
    withJsonBody[DraftReturn] { draftReturn =>
      draftReturnsService
        .saveDraftReturn(draftReturn, CgtReference(cgtReference))
        .fold(
          { e =>
            logger.warn("Could not store draft return", e)
            InternalServerError
          },
          _ => Ok
        )
    }
  }

  def deleteDraftReturns(): Action[JsValue] = authenticate(parse.json).async { implicit request =>
    withJsonBody[DeleteDraftReturnsRequest] { deleteRequest =>
      draftReturnsService
        .deleteDraftReturns(deleteRequest.draftReturnIds)
        .fold(
          { e =>
            logger.warn(s"Could not delete draft return with ids ${deleteRequest.draftReturnIds}", e)
            InternalServerError
          },
          _ => Ok
        )

    }

  }

}

object DraftReturnsController {

  final case class DeleteDraftReturnsRequest(draftReturnIds: List[UUID])

  object DeleteDraftReturnsRequest {

    implicit val format: OFormat[DeleteDraftReturnsRequest] = Json.format

  }

}
