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

package uk.gov.hmrc.cgtpropertydisposals.controllers

import com.google.inject.Inject
import play.api.libs.json.Json
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.models.NINO
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.ExecutionContext

class BusinessPartnerRecordController @Inject() (
    bprService: BusinessPartnerRecordService,
    cc: ControllerComponents
)(implicit ec: ExecutionContext) extends BackendController(cc) with Logging {

  def getBusinessPartnerRecord(nino: NINO): Action[AnyContent] = Action.async { implicit request =>
    bprService.getBusinessPartnerRecord(nino).map {
      case Left(e) =>
        logger.warn(s"Could not get BPR for NINO ${nino.value}", e)
        InternalServerError

      case Right(bpr) =>
        Ok(Json.toJson(bpr))
    }

  }

}
