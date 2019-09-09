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

import cats.syntax.either._
import com.google.inject.Inject
import julienrf.json.derived
import play.api.libs.json.{JsValue, Json, Reads}
import play.api.mvc.{Action, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.models.{BprRequest, DateOfBirth, NINO, Name, SAUTR}
import uk.gov.hmrc.cgtpropertydisposals.models.EitherFormat.eitherFormat
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

  import uk.gov.hmrc.cgtpropertydisposals.controllers.BusinessPartnerRecordController.IncomingRequest
  import uk.gov.hmrc.cgtpropertydisposals.controllers.BusinessPartnerRecordController.IncomingRequest._

  def getBusinessPartnerRecord(): Action[JsValue] = Action(parse.json).async { implicit request =>
    request.body
      .validate[IncomingRequest]
      .fold(
        jsError => {
          logger.error(s"Bad JSON payload ${jsError.toString}")
          Future.successful(BadRequest)
        },
        incomingRequest => {
          val bprRequest = toBprRequest(incomingRequest)
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
      )
  }

  private def toBprRequest(incomingRequest: IncomingRequest): BprRequest =
    incomingRequest match {
      case IndividualBprRequest(id, forename, surname, dateOfBirth, requiresNameMatch) =>
        BprRequest(Right(
          BprRequest.Individual(
            id.bimap(SAUTR(_), NINO(_)),
            Name(forename, surname),
            dateOfBirth.map(DateOfBirth(_)))
        ),
          requiresNameMatch
        )

      case OrganisationBprRequest(sautr, requiresNameMatch) =>
        BprRequest(Left(BprRequest.Organisation(SAUTR(sautr))), requiresNameMatch)
    }

}


object BusinessPartnerRecordController {

  private sealed trait IncomingRequest

  private object IncomingRequest {
    final case class IndividualBprRequest(id: Either[String,String],
                                          forename: String,
                                          surname: String,
                                          dateOfBirth: Option[LocalDate],
                                          requiresNameMatch: Boolean
                                         ) extends IncomingRequest

    final case class OrganisationBprRequest(sautr: String,
                                            requiresNameMatch: Boolean
                                           ) extends IncomingRequest


    implicit val individualBprRequestReads: Reads[IndividualBprRequest] = Json.reads[IndividualBprRequest]

    @SuppressWarnings(Array("org.wartremover.warts.PublicInference"))
    implicit val format: Reads[IncomingRequest] = derived.reads[IncomingRequest]

  }

}
