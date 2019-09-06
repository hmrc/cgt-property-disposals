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
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.cgtpropertydisposals.controllers.BusinessPartnerRecordController.IndividualBprRequest
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, DateOfBirth, NINO, Name, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.service.BusinessPartnerRecordService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.cgtpropertydisposals.util.Logging._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class BusinessPartnerRecordController @Inject()(
                                                 bprService: BusinessPartnerRecordService,
                                                 cc: ControllerComponents
                                               )(implicit ec: ExecutionContext)
  extends BackendController(cc)
    with Logging {

  def getBusinessPartnerRecordFromNino(nino: String): Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body
      .validate[IndividualBprRequest]
      .fold(
        jsError => {
          logger.error(s"Bad JSON payload ${jsError.toString}")
          Future.successful(BadRequest)
        },
        i => getBusinessPartnerRecord(
          toIndividualBprRequest(Right(NINO(nino)), i))
      )
  }

  def getBusinessPartnerRecordFromSautr(sautr: String): Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body
      .validateOpt[IndividualBprRequest]
      .fold(
        jsError => {
          logger.error(s"Bad JSON payload ${jsError.toString}")
          Future.successful(BadRequest)
        },
        maybeIndividual => {
          val bprRequest =
            maybeIndividual
              .fold(
                BprRequest(Left(BprRequest.Organisation(SAUTR(sautr))))
              )(
                toIndividualBprRequest(Left(SAUTR(sautr)), _)
              )
          getBusinessPartnerRecord(bprRequest)
        }
      )
  }

  private def toIndividualBprRequest(id: Either[SAUTR,NINO], request: IndividualBprRequest): BprRequest =
    BprRequest(Right(
      BprRequest.Individual(id, Name(request.forename, request.surname), request.dateOfBirth.map(DateOfBirth(_)))
    ))

  private def getBusinessPartnerRecord(bprRequest: BprRequest)(
    implicit hc: HeaderCarrier
  ): Future[Result] =
    bprService
      .getBusinessPartnerRecord(bprRequest)
      .value
      .map {
        case Left(e) =>
          logger.warn(s"Failed to get BPR for id ${bprRequest.id}", e)
          InternalServerError
        case Right(bpr) =>
          Ok(Json.toJson(bpr))
      }

}


object BusinessPartnerRecordController {

  private final case class IndividualBprRequest(forename: String, surname: String, dateOfBirth: Option[LocalDate])

  private object IndividualBprRequest {
    implicit val format: OFormat[IndividualBprRequest] = Json.format[IndividualBprRequest]
  }


}
