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

package uk.gov.hmrc.cgtpropertydisposals.controllers.onboarding

import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.onboarding.bpr.BusinessPartnerRecordRequest
import uk.gov.hmrc.cgtpropertydisposals.service.onboarding.BusinessPartnerRecordService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class BusinessPartnerRecordController @Inject() (
  authenticate: AuthenticateActions,
  bprService: BusinessPartnerRecordService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def getBusinessPartnerRecord(): Action[JsValue] =
    authenticate(parse.json).async { implicit request =>
      request.body
        .validate[BusinessPartnerRecordRequest]
        .fold(
          jsError => {
            logger.error(s"Bad JSON payload ${jsError.toString}")
            Future.successful(BadRequest)
          },
          bprRequest =>
            bprService
              .getBusinessPartnerRecord(bprRequest)
              .value
              .map {
                case Left(e)    =>
                  logger.warn(s"Failed to get BPR for id ${bprRequest.foldOnId(_.toString, _.toString, _.toString)}", e)
                  InternalServerError
                case Right(bpr) =>
                  Ok(Json.toJson(bpr))
              }
        )
    }
}
