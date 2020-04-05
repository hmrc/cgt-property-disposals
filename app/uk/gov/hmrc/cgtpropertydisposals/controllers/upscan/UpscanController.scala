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

package uk.gov.hmrc.cgtpropertydisposals.controllers.upscan

import cats.data.EitherT
import cats.instances.future._
import cats.instances.string._
import cats.syntax.eq._
import com.google.inject.Inject
import play.api.libs.json.{JsValue, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.cgtpropertydisposals.controllers.actions.AuthenticateActions
import uk.gov.hmrc.cgtpropertydisposals.models.Error
import uk.gov.hmrc.cgtpropertydisposals.models.ids.DraftReturnId
import uk.gov.hmrc.cgtpropertydisposals.models.upscan.{UpscanCallBack, UpscanReference, UpscanUpload}
import uk.gov.hmrc.cgtpropertydisposals.service.upscan.UpscanService
import uk.gov.hmrc.cgtpropertydisposals.util.Logging
import uk.gov.hmrc.play.bootstrap.controller.BackendController

import scala.concurrent.{ExecutionContext, Future}

class UpscanController @Inject() (
  authenticate: AuthenticateActions,
  upscanService: UpscanService,
  cc: ControllerComponents
)(
  implicit ec: ExecutionContext
) extends BackendController(cc)
    with Logging {

  private val READY_FOR_DOWNLOAD = "READY"
  private val FAILED_UPSCAN      = "FAILED"

  def getUpscanUpload(draftReturnId: DraftReturnId, upscanReference: UpscanReference): Action[AnyContent] =
    authenticate.async {
      upscanService
        .readUpscanUpload(draftReturnId, upscanReference)
        .fold(
          e => {
            logger.warn(s"could not get upscan upload: $e")
            InternalServerError
          }, {
            case Some(upscanUpload) =>
              Ok(Json.toJson(upscanUpload))
            case None =>
              logger.info(
                s"could not find an upscan upload with draft " +
                  s"return id $draftReturnId and upscan reference $upscanReference"
              )
              BadRequest
          }
        )
    }

  def saveUpscanUpload(): Action[JsValue] =
    authenticate(parse.json).async { implicit request =>
      request.body
        .asOpt[UpscanUpload] match {
        case Some(upscanUpload) =>
          upscanService
            .storeUpscanUpload(upscanUpload)
            .fold(
              e => {
                logger.warn(s"could not save upscan upload: $e")
                InternalServerError
              },
              _ => Ok
            )
        case None =>
          logger.warn(s"could not parse JSON body")
          Future.successful(BadRequest)
      }
    }

  def updateUpscanUpload(draftReturnId: DraftReturnId, upscanReference: UpscanReference): Action[JsValue] =
    authenticate(parse.json).async { implicit request =>
      request.body
        .asOpt[UpscanUpload] match {
        case Some(upscanUpload) =>
          upscanService
            .updateUpscanUpload(draftReturnId, upscanReference, upscanUpload)
            .fold(
              e => {
                logger.warn(s"could not update upscan upload: $e")
                InternalServerError
              },
              _ => Ok
            )
        case None =>
          logger.warn(s"could not parse JSON body")
          Future.successful(BadRequest)
      }
    }

  def callback(draftReturnId: DraftReturnId, upscanReference: UpscanReference): Action[JsValue] =
    Action.async(parse.json) { implicit request =>
      (request.body \ "fileStatus").asOpt[String] match {
        case Some(upscanStatus) =>
          if (upscanStatus === READY_FOR_DOWNLOAD | upscanStatus === FAILED_UPSCAN) {
            val result = for {
              maybeUpscanUpload <- upscanService.readUpscanUpload(draftReturnId, upscanReference)
              upscanUpload <- EitherT.fromOption(
                               maybeUpscanUpload,
                               Error(
                                 s"could not get upscan upload value from db for draft return id $draftReturnId and upscan reference $upscanReference"
                               )
                             )
              callBackResult <- EitherT.fromOption(
                                 request.body.asOpt[UpscanCallBack],
                                 Error(
                                   s"could not parse upscan call back response body : ${request.body.toString}"
                                 )
                               )

              newUpscanUpload = upscanUpload.copy(upscanCallBack = Some(callBackResult))
              _ <- upscanService.updateUpscanUpload(draftReturnId, upscanReference, newUpscanUpload)
            } yield ()

            result.fold(
              e => {
                logger.warn(s"could not process upscan call back : $e")
                InternalServerError
              },
              _ => {
                logger.info(s"updated upscan upload with upscan call back result")
                NoContent
              }
            )

          } else {
            logger.warn(s"could not process upscan status : ${request.body.toString}")
            Future.successful(InternalServerError)
          }
        case None =>
          logger.warn(s"could not parse upscan response body : ${request.body.toString}")
          Future.successful(InternalServerError)
      }
    }
}
