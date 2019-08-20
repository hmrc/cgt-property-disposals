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

import java.time.LocalDate

import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, DateOfBirth, NINO, Name}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class BusinessPartnerRecordController @Inject()(
  bprService: BusinessPartnerRecordService,
  cc: ControllerComponents
)(implicit ec: ExecutionContext)
    extends BackendController(cc)
    with Logging {

  def getBusinessPartnerRecord: Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body
      .validate[BprRequest]
      .fold(
        jsError => {
          logger.error(s"Bad JSON payload ${jsError.toString}")
          Future.successful(BadRequest)
        },
        bprRequest => {
          bprService
            .getBusinessPartnerRecord(
              NINO(bprRequest.nino),
              Name(bprRequest.fname, bprRequest.lname),
              DateOfBirth(LocalDate.parse(bprRequest.dob))
            )
            .value
            .map {
              case Left(e) =>
                logger.warn(s"Failed to get BPR with following parameters ${bprRequest.toString}", e)
                InternalServerError
              case Right(bpr) =>
                Ok(Json.toJson(bpr))
            }
        }
      )
  }
}
